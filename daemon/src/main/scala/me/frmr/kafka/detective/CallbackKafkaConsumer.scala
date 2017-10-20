package me.frmr.kafka.detective

import com.typesafe.scalalogging.StrictLogging
import java.util.{Map => JMap, _}
import java.util.concurrent.atomic.AtomicLong
import org.apache.kafka.common._
import org.apache.kafka.common.errors._
import org.apache.kafka.clients.consumer._
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.control._

/**
 * A wraper for a consumer implementation that invokes a callback function for each record that
 * appears to the consumer.
 *
 * All callbacks must be added to this consumer before starting the underlying consumer thread.
 *
 * Callbacks will run on the same thread as the consumer.
 *
 * Thrown execeptions in callbacks will result in the consumer shutting down without committing
 * offsets, so an exception should be a pretty safe way to abort if things go sideways in your
 * callback code.
 */
class CallbackKafkaConsumer[KeyType, ValueType](
  topic: String,
  partitions: Seq[Int],
  properties: Properties,
  consumerRebalanceListener: Option[ConsumerRebalanceListener] = None,
  var backpressureFn: Option[CallbackKafkaConsumer.BackpressureFn] = None
) extends StrictLogging {
  type CallbackFn = (ConsumerRecord[KeyType, ValueType])=>Unit
  type BatchCallbackFn = (ConsumerRecords[KeyType, ValueType])=>Unit
  type ResetCallbackFn = ()=>Unit

  private[this] val loggingIdentifier = topic + "-" + partitions.mkString(",")

  @volatile
  private[this] var running = false

  @volatile
  private[this] var resetTriggered = false

  @volatile
  private[this] var resetProcessed = false

  @volatile
  private[this] var hasBeenUsed = false

  @volatile
  private[this] var callbacks: Seq[CallbackFn] = Seq.empty

  @volatile
  private[this] var batchCallbacks: Seq[BatchCallbackFn] = Seq.empty

  @volatile
  private[this] var resetCallbacks: Seq[ResetCallbackFn] = Seq.empty

  @volatile
  private[this] var firstOffsetByPartition: Map[Int, Option[Long]] = partitions.map({ partition =>
    partition -> None
  }).toMap

  private[this] val messagesConsumed: AtomicLong = new AtomicLong()

  private[this] val messagesConsumedByPartition: Map[Int, AtomicLong] = partitions.map({ partition =>
    partition -> new AtomicLong()
  }).toMap

  properties.put("enable.auto.commit", "false")
  protected[this] lazy val consumer: Consumer[KeyType, ValueType] = new KafkaConsumer(properties)

  def getRunning: Boolean = running

  def getHasBeenUsed: Boolean = hasBeenUsed

  def getResetProcessed: Boolean = resetProcessed

  def addCallback(newFn: CallbackFn) = {
    if (running) {
      throw new RuntimeException("Cannot add callbacks after the consumer has been started.")
    }

    callbacks = callbacks :+ newFn
  }

  def addBatchCallback(newFn: BatchCallbackFn) = {
    if (running) {
      throw new RuntimeException("Cannot add callbacks after the consumer has been started.")
    }

    batchCallbacks = batchCallbacks :+ newFn
  }

  def addResetCallback(newFn: ResetCallbackFn) = {
    if (running) {
      throw new RuntimeException("Cannot add reset callbacks after the consumer has been started.")
    }

    resetCallbacks = resetCallbacks :+ newFn
  }

  def setBackpressureFn(newFn: Option[CallbackKafkaConsumer.BackpressureFn]): Unit = {
    backpressureFn = newFn
  }

  def getPositionAheadOfStart: Long = {
    messagesConsumed.longValue()
  }

  def getPositionAheadOfStartByPartition(partition: Int): Long = {
    val partitionCounter = messagesConsumedByPartition.get(partition).getOrElse {
      throw new RuntimeException(s"Cannot find partition counter for partition $partition in this consumer.")
    }

    partitionCounter.longValue()
  }

  def getOffsetDifferenceFromStart(partition: Int, offset: Long): Long = {
    firstOffsetByPartition.get(partition) match {
      case Some(Some(firstOffsetLong)) =>
        offset - firstOffsetLong

      case _ =>
        0
    }
  }

  def isBackpressuring: Boolean = {
    backpressureFn.map(_.apply()).getOrElse(false)
  }

  @tailrec
  private[detective] final def waitUntilRunning(retriesRemaining: Int = 5, sleepMs: Int = 10): Unit = {
    Thread.sleep(sleepMs)

    if (running == false && retriesRemaining > 0) {
      waitUntilRunning(retriesRemaining - 1, sleepMs)
    }
  }

  private[this] val assignments = partitions.map({ partitionNumber =>
    new TopicPartition(topic, partitionNumber)
  }).asJava

  private[detective] def doAssignments(): Unit = {
    if (partitions.isEmpty) {
      throw new IllegalStateException("The Callback consumer requires that partitions are provided to it.")
    } else {
      consumer.assign(assignments)
      consumer.seekToEnd(assignments)
    }
  }

  def start(): Unit = {
    if (hasBeenUsed) {
      throw new RuntimeException("This callback kafka consumer has already been used. Please instantiate a new one.")
    }

    doAssignments()
    consumingThread.start()
    waitUntilRunning()
  }

  def stop(): Unit = {
    if (running) {
      consumer.wakeup()

      // We need to handle the case that the consumer could be in a backpressure loop. We wait
      // 500 ms to shut things down and then essentially do a force-shutdown by setting running
      // to false if the thread is still alive.
      consumingThread.join(500)

      if (consumingThread.isAlive()) {
        running = false
        consumingThread.join()
      }
    }
  }

  /**
   * Reset the consumer to the end of the topic assignments and put the consumer into a paused
   * state. In order to resume, the calling code will need to invoke [[resume]].
   */
  def reset(): Unit = {
    logger.debug(s"$loggingIdentifier: Reset requested")
    resetTriggered = true
  }

  /**
   * Resume consumption after a reset.
   */
  def resume(): Unit = {
    logger.debug(s"$loggingIdentifier: Resume requested")
    resetTriggered = false
    resetProcessed = false
  }

  private[this] lazy val consumingThread = new Thread(topic + "-" + partitions.mkString("-") + "-consuming-thread") {
    override def run(): Unit = {
      logger.debug(s"Consuming thread for ${topic}-${partitions.mkString(",")} is starting")
      running = true
      hasBeenUsed = true
      var needsSeekToEnd = false

      while (running) {
        try {
          if (resetTriggered == true && resetProcessed == false) {
            needsSeekToEnd = true
            messagesConsumed.set(0L)
            messagesConsumedByPartition.values.foreach(_.set(0L))
            firstOffsetByPartition = firstOffsetByPartition.mapValues(_ => None)
            resetCallbacks.foreach(_.apply())

            logger.debug(s"$loggingIdentifier: Reset processed.")
            resetProcessed = true
          } else if (resetTriggered == true && resetProcessed == true) {
            Thread.sleep(2)
          } else if (isBackpressuring) {
            Thread.sleep(100)
          } else {
            if (needsSeekToEnd) {
              needsSeekToEnd = false
              consumer.seekToEnd(assignments)
            }

            val records = consumer.poll(100)

            if (! records.isEmpty()) {
              val recordsSeq = records.asScala.toSeq

              if (firstOffsetByPartition.toList.find(_._2.isEmpty).isDefined) {
                firstOffsetByPartition = firstOffsetByPartition.map {
                  case (partition, None) =>
                    val firstOffset = recordsSeq.find(_.partition() == partition).map(_.offset())
                    (partition, firstOffset)

                  case (partition, firstOffset) =>
                    (partition, firstOffset)
                }
              }

              if (batchCallbacks.nonEmpty) {
                batchCallbacks.foreach(callback => callback(records))
              }

              if (callbacks.nonEmpty) {
                records.asScala.toSeq.foreach { record =>
                  callbacks.foreach(callback => callback(record))
                }
              }

              consumer.commitAsync()

              recordsSeq.groupBy(_.partition()).mapValues(_.size).foreach {
                case (partition, numRecords) =>
                  messagesConsumedByPartition.get(partition).foreach(_.getAndAdd(numRecords))
              }

              messagesConsumed.getAndAdd(recordsSeq.length)
            }
          }
        } catch {
          case wakeup: WakeupException =>
            // shut down
            logger.debug(s"Consumer for ${topic}-${partitions.mkString(",")} is shutting down.")
            running = false

          case NonFatal(otherException) =>
            logger.error(s"An unexpected exception occured during message consumption for ${topic}-${partitions.mkString(",")}. Consumer will shut down.", otherException)
            running = false

          case _: Throwable =>
            running = false
        }
      }

      consumer.close()
    }
  }
}
object CallbackKafkaConsumer {
  type BackpressureFn = ()=>Boolean
  type CallbackFn[KeyType, ValueType] = (ConsumerRecord[KeyType, ValueType])=>Unit
  type BatchCallbackFn[KeyType, ValueType] = (ConsumerRecords[KeyType, ValueType])=>Unit
  type ResetCallbackFn = ()=>Unit
}
