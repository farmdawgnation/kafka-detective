package me.frmr.kafka.detective

import me.frmr.kafka.detective.api._
import com.typesafe.scalalogging.StrictLogging
import java.util._
import org.apache.kafka.clients.consumer._
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
 * A wrapper for a KafkaConsumer that stores incoming messages in a fixed-size window, eliminating
 * old messages from the winow as new ones come in.
 */
class WindowingKafkaConsumer(
  windowSize: Int,
  topic: String,
  partitions: Seq[Int],
  properties: Properties,
  deserializer: MonitorDeserializer,
  metricsNamespace: String,
  backpressureFn: Option[CallbackKafkaConsumer.BackpressureFn] = None,
  onConsumeFn: Option[CallbackKafkaConsumer.BatchCallbackFn[Array[Byte], Array[Byte]]] = None
) extends Metrics with StrictLogging {
  @volatile
  private[this] var currentWindow: Seq[MonitorObjectEnvelope] = Seq.empty

  val deserializationTimer = metrics.timer(s"$metricsNamespace.reference-deserialization-time")

  protected lazy val callbackConsumer = new CallbackKafkaConsumer[Array[Byte], Array[Byte]](
    topic,
    partitions,
    properties,
    backpressureFn = backpressureFn
  )

  private[detective] def doAssignments(): Unit = callbackConsumer.doAssignments

  def getCurrentWindow(): Seq[MonitorObjectEnvelope] = currentWindow

  def getRunning: Boolean = callbackConsumer.getRunning

  def getHasBeenUsed: Boolean = callbackConsumer.getHasBeenUsed

  def getPositionAheadOfStart: Long = callbackConsumer.getPositionAheadOfStart
  
  def getPositionAheadOfStartByPartition(partition: Int): Long = callbackConsumer.getPositionAheadOfStartByPartition(partition)

  def getOffsetDifferenceFromStart(partition: Int, offset: Long): Long = callbackConsumer.getOffsetDifferenceFromStart(partition, offset)

  def getResetProcessed: Boolean = callbackConsumer.getResetProcessed

  def start(): Unit = {
    if (getHasBeenUsed) {
      throw new RuntimeException("This windowing kafka consumer has already been used. Please instantiate a new one.")
    }

    callbackConsumer.addBatchCallback(handler _)
    onConsumeFn.foreach(callbackConsumer.addBatchCallback)
    callbackConsumer.addResetCallback(() => currentWindow = Seq.empty)
    callbackConsumer.start()
  }

  def stop(): Unit = callbackConsumer.stop()

  def reset(): Unit = callbackConsumer.reset()

  def resume(): Unit = callbackConsumer.resume()

  def handler(records: ConsumerRecords[Array[Byte], Array[Byte]]): Unit = {
    try {
      val startingWindow = currentWindow
      val deserializedRecords = deserializationTimer.time {
        records.asScala.par.map({ record =>
          deserializer.deserialize(
            record.offset(),
            record.partition(),
            record.timestamp(),
            record.key(),
            record.value()
          )
        }).toSeq
      }
      val unsizedWindow = startingWindow ++ deserializedRecords

      val sizedWindow = if (unsizedWindow.length > windowSize) {
        unsizedWindow.drop(unsizedWindow.length - windowSize)
      } else {
        unsizedWindow
      }

      currentWindow = sizedWindow
    } catch {
      case NonFatal(e) =>
        logger.error("Exception while handling reference winodw message", e)
    }
  }
}
