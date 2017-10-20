package me.frmr.kafka.detective.matchfinder

import me.frmr.kafka.detective.api._
import com.typesafe.scalalogging.StrictLogging
import java.security._
import java.time._
import java.util.Arrays
import java.util.concurrent._
import java.util.concurrent.locks._
import javax.xml.bind.DatatypeConverter
import scala.collection.JavaConverters._

/**
 * A simple match finder that checks for equivalent keys.
 *
 * This class will work with byte arrays or any object that has a working equals implementation
 * (for example, String).
 *
 * This class will attempt to handle multiple occurences of the same key by storing the most recent
 * reference topic offset that a key has been seen at and then ignoring any reference message that
 * occur before that offset. This implementation is most efficient if your keys are Strings or if
 * they are objects that have a working toString method. Byte arrays will have to be SHA-1 hashed
 * and converted to a hex string internally for us with this finder.
 *
 * This finder will periodically do garbage collection on its internal map of seen keys to avoid
 * blowing the heap. The actual collection will occur outside of the main thread and remove keys
 * whose last seen offset is less than 1000 less than the current start offset of the reference
 * window.
 */
class KeyEqualsFinder extends MonitorMatchFinder with StrictLogging {
  private[this] val seenKeys = new ConcurrentHashMap[String, Long]().asScala
  private[this] val seenKeysLock = new ReentrantReadWriteLock(true)
  private[this] var lastSeenKeysCleaning: Instant = Instant.now()

  /**
   * Generates a string that can be used as the key to the seenKeys method.
   */
  private[this] def seenKeysKeyFor(input: Object): String = {
    input match {
      case byteArray: Array[Byte] =>
        val shaDigester = MessageDigest.getInstance("SHA-1")
        val shaBytes = shaDigester.digest(byteArray)
        DatatypeConverter.printHexBinary(shaBytes)

      case str: String =>
        str

      case other =>
        other.toString
    }
  }

  private[this] def seenKeysNeedsCleaning(): Boolean = {
    seenKeys.keySet.size > 1000 &&
    Instant.now().isAfter(lastSeenKeysCleaning.plusSeconds(300))
  }

  /**
   * Prune offsets that no longer matter
   */
  private[this] def pruneSeenKeys(referenceWindow: Seq[MonitorObjectEnvelope]): Unit = {
    seenKeysLock.writeLock().lock()

    if (seenKeysNeedsCleaning() && referenceWindow.length > 0) {
      logger.trace("Cleaning seen keys")
      val startOffset = referenceWindow.head.messageOffset
      val keysToRemove = seenKeys.filter(_._2 < startOffset).keySet

      keysToRemove.foreach(seenKeys.remove)
      logger.trace("Seen keys is clean")
    }
    lastSeenKeysCleaning = Instant.now()

    seenKeysLock.writeLock().unlock()
  }

  private[this] def markKeySeen(key: String, offset: Long): Unit = {
    seenKeys.put(key, offset)
  }

  private[this] def lastSeenOffsetFor(key: String): Long = {
    seenKeysLock.readLock().lock()
    val lastSeenOffset = seenKeys.get(key).getOrElse(-1L)
    seenKeysLock.readLock().unlock()

    lastSeenOffset
  }

  override def find(
    testMessage: MonitorObjectEnvelope,
    referenceWindow: Seq[MonitorObjectEnvelope]
  ): MonitorMatchFindResult = {
    if (seenKeysNeedsCleaning()) {
      pruneSeenKeys(referenceWindow)
    }

    val testRecordKey = seenKeysKeyFor(testMessage.keyInstance)
    val lastSeenOffsetOfKey = lastSeenOffsetFor(testRecordKey)

    def finder(possibleMatch: MonitorObjectEnvelope) = {
      (testMessage.keyInstance, possibleMatch.keyInstance) match {
        case _ if possibleMatch.messageOffset <= lastSeenOffsetOfKey =>
          false

        case (testKey: Array[Byte], possibleKey: Array[Byte]) =>
          Arrays.equals(testKey, possibleKey)

        case (testKey, possibleKey) =>
          possibleKey.equals(testKey)
      }
    }

    referenceWindow
      .find(finder _)
      .map(envelope => {
        markKeySeen(testRecordKey, envelope.messageOffset)
        envelope
      })
      .map(FoundMatch(_))
      .getOrElse(NoMatchFound())
  }
}
