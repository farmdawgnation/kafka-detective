package me.frmr.kafka.detective.monitor

import me.frmr.kafka.detective._
import me.frmr.kafka.detective.api._
import me.frmr.kafka.detective.domain._
import me.frmr.kafka.detective.util._
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer._
import java.security.MessageDigest
import java.util.concurrent._
import javax.xml.bind.DatatypeConverter
import scala.math
import scala.util.control.NonFatal

/**
 * A {{MonitorWorker}} oversees an individual consumer pair doing monitoring of a set of topics.
 *
 * The worker has a single-threaded thread pool that will be used to queue up handling of messages
 * that come in from the test consumer. The worker will block that thread for each message until
 * either a suitable match is found in the reference window (which changes constantly) or until
 * all retries have been exhausted. This ensures in-order matching of messages coming in from the
 * test consumer.
 *
 * @param monitor The monitor this worker is supposed to be used for.
 * @param workerNumber Specifies which worker number this worker is for the monitor. Used for thread naming.
 * @param numberOfComparisonThreads The number of comparison threads to use for tests
 * @param emptyQueuePollIntervalMs The interval in ms to poll the queue length and determine if it's empty or not
 * @param queueResetMs The number of ms the queue should be non-empty before triggering a reset.
 */
class MonitorWorker(
  monitor: Monitor,
  workerNumber: Int,
  numberOfComparisonThreads: Int,
  emptyQueuePollIntervalMs: Int,
  queueResetMs: Int
) extends Metrics with StrictLogging {
  private[this] val metricsNamespace = s"worker.${monitor.identifier}.${workerNumber}"
  lazy val messageDeserializationTimer = metrics.timer(s"$metricsNamespace.message-deserialization-time")
  lazy val messageMatchFindTimer = metrics.timer(s"$metricsNamespace.message-match-find-time")

  lazy val testMessageConsumed = metrics.meter(s"$metricsNamespace.test-message-consumed")
  lazy val testMessageProcessed = metrics.meter(s"$metricsNamespace.test-message-processed")
  lazy val testMessageErrored = metrics.meter(s"$metricsNamespace.test-message-errored")

  lazy val referenceMessageConsumed = metrics.meter(s"$metricsNamespace.reference-message-consumed")

  lazy val successLoopCount = metrics.histogram(s"$metricsNamespace.loop-count-by-result.success")
  lazy val noMatchLoopCount = metrics.histogram(s"$metricsNamespace.loop-count-by-result.no-match")
  lazy val failureLoopCount = metrics.histogram(s"$metricsNamespace.loop-count-by-result.failure")
  lazy val ignoreLoopCount = metrics.histogram(s"$metricsNamespace.loop-count-by-result.ignore")

  val referenceDeserializer = monitor.referenceSubject.deserializer()
  val testDeserializer = monitor.testSubject.deserializer()

  val monitorMatchFinder = monitor.matchFinder()
  val monitorMatchTester = monitor.matchTester()
  val monitorReporters = monitor.reporters()

  val zippedPartitions = monitor.referenceSubject.onlyPartitions.zip(monitor.testSubject.onlyPartitions)

  val executor: KeyAwareExecutor = {
    val workQueue: BlockingQueue[Runnable] = new LinkedBlockingQueue[Runnable]()
    val threadPrefix = s"${monitor.identifier}-worker-$workerNumber-comparison-thread"
    new KeyAwareExecutor(
      numberOfComparisonThreads,
      numberOfComparisonThreads,
      60,
      TimeUnit.MINUTES,
      workQueue,
      threadPrefix
    )
  }

  val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

  val queueSizeMovingAverage = new MovingAverage()

  metrics.gauge(s"$metricsNamespace.executor-queue-size") {
    val queueLen = executor.getQueue().size
    queueSizeMovingAverage.addSample(queueLen)
    queueLen
  }

  metrics.gauge(s"$metricsNamespace.reference-delta-ahead-of-test") {
    referenceConsumer.getPositionAheadOfStart - testConsumer.getPositionAheadOfStart
  }

  @volatile
  var lastOffsetProcessed: Long = 0

  @volatile
  var resettingWorker: Boolean = false

  private[this] val resetterHandler: Runnable = new Runnable {
    def run(): Unit = {
      if (resettingWorker) {
        val testHealthy = ! shouldTestConsumerBackpressure()
        val referenceHealthy = ! shouldReferenceConsumerBackpressure()
        val queueEmpty = executor.getQueue().size == 0

        if (queueEmpty && testHealthy && referenceHealthy && testConsumer.getResetProcessed && referenceConsumer.getResetProcessed) {
          logger.info(s"Worker ${monitor.identifier}-$workerNumber is returning to normal")
          resettingWorker = false
          referenceConsumer.resume()
          testConsumer.resume()
          logger.info(s"Worker ${monitor.identifier}-$workerNumber is still waiting for all resets to be processed")
        }
      } else {
        val testUnhealthy = (System.currentTimeMillis() - lastHealthyTestConsumerTime) > queueResetMs
        val referenceUnhealthy = (System.currentTimeMillis() - lastHealthyReferenceConsumerTime) > queueResetMs

        if (testUnhealthy || referenceUnhealthy) {
          logger.info(s"Worker ${monitor.identifier}-$workerNumber is entering reset mode. testUnhealthy=$testUnhealthy referenceUnhealthy=$referenceUnhealthy")
          testConsumer.reset()
          referenceConsumer.reset()
          resettingWorker = true
        }
      }
    }
  }
  val queueResetterSchedule = scheduledExecutor.scheduleAtFixedRate(
    resetterHandler,
    emptyQueuePollIntervalMs,
    emptyQueuePollIntervalMs,
    TimeUnit.MILLISECONDS
  )

  @volatile
  var lastHealthyTestConsumerTime: Long = System.currentTimeMillis()

  val backpressureCeiling = monitor.referenceWindowSize / 2

  def shouldTestConsumerBackpressure(): Boolean = {
    val shouldBackpressure = executor.getQueue().size > backpressureCeiling

    if (! shouldBackpressure && queueSizeMovingAverage.getCurrentAverage < backpressureCeiling)
      lastHealthyTestConsumerTime = System.currentTimeMillis()

    shouldBackpressure
  }

  @volatile
  var lastHealthyReferenceConsumerTime: Long = System.currentTimeMillis()

  def shouldReferenceConsumerBackpressure(): Boolean = {
    val maxDeltaBetweenConsumerPositions = zippedPartitions.map({
      case (refPartition, testPartition) =>
        referenceConsumer.getPositionAheadOfStartByPartition(refPartition) - testConsumer.getPositionAheadOfStartByPartition(testPartition)
    }).max

    val shouldBackpressure = maxDeltaBetweenConsumerPositions > backpressureCeiling

    if (! shouldBackpressure)
      lastHealthyReferenceConsumerTime = System.currentTimeMillis()

    shouldBackpressure
  }

  val referenceConsumer = {
    val properties = new java.util.Properties()
    properties.put("bootstrap.servers", monitor.referenceSubject.bootstrapServer)
    properties.put("group.id", s"${monitor.identifier}-ref-consumer")
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
    properties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")

    def metricsCounter(records: ConsumerRecords[Array[Byte], Array[Byte]]): Unit = {
      referenceMessageConsumed.mark(records.count())
    }

    new WindowingKafkaConsumer(
      monitor.referenceWindowSize,
      monitor.referenceSubject.topic,
      monitor.referenceSubject.onlyPartitions,
      properties,
      referenceDeserializer,
      metricsNamespace,
      backpressureFn = Some(shouldReferenceConsumerBackpressure _),
      onConsumeFn = Some(metricsCounter)
    )
  }

  val testConsumer = {
    val properties = new java.util.Properties()
    properties.put("bootstrap.servers", monitor.testSubject.bootstrapServer)
    properties.put("group.id", s"${monitor.identifier}-test-consumer")
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
    properties.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")

    new CallbackKafkaConsumer[Array[Byte], Array[Byte]](
      monitor.testSubject.topic,
      monitor.testSubject.onlyPartitions,
      properties,
      backpressureFn = Some(shouldTestConsumerBackpressure _)
    )
  }

  /**
   * Processes an individual test record from the test topic.
   */
  def handleMessage(deserialized: MonitorObjectEnvelope): Unit = {
    try {
      var loopCount: Int = 0
      val currentRecordPositionAheadOfStart = testConsumer.getOffsetDifferenceFromStart(deserialized.messagePartition, deserialized.messageOffset)
      val testPartition = deserialized.messagePartition
      val matchingReferencePartition = zippedPartitions.find(_._2 == testPartition).map(_._1).getOrElse {
        throw new RuntimeException(s"Could not find a matching reference partition for $testPartition")
      }

      // The test subject is possibly within the reference window if the current reference position
      // minus the refernece window size is less than or equal to the current record position
      // ahead of start
      def testSubjectAfterFirstReferenceMessage = {
        (referenceConsumer.getPositionAheadOfStartByPartition(matchingReferencePartition) - monitor.referenceWindowSize) <= currentRecordPositionAheadOfStart
      }

      // The test subject is ahead of the end of the reference window if the current record position
      // ahead of start is larger than the reference consumer's position ahead of start
      def testSubjectAfterLastReferenceMessage = {
        currentRecordPositionAheadOfStart >= referenceConsumer.getPositionAheadOfStartByPartition(matchingReferencePartition)
      }

      // If the test subject occurs after the start of the reference window _and_ after the
      // end of the reference window, spin wait until one of these conditions is no longer true
      while (! resettingWorker && testSubjectAfterFirstReferenceMessage && testSubjectAfterLastReferenceMessage) {
        loopCount = loopCount + 1
        Thread.`yield`()
        Thread.sleep(10)
      }

      if (! testSubjectAfterFirstReferenceMessage || resettingWorker) {
        // This message exists before the beginning of the reference window, so we mark it as
        // and ignored message so we don't block the queue in the case that the reference window
        // has already moved way past the messages we're currently considering.
        monitorReporters.foreach(_.reportIgnored(deserialized))
        ignoreLoopCount += loopCount
      } else {
        val currentReferenceWindow = referenceConsumer.getCurrentWindow

        val matchResult = messageMatchFindTimer.time {
          monitorMatchFinder.find(deserialized, currentReferenceWindow)
        }

        matchResult match {
          case FoundMatch(matchingReference) =>
            monitorMatchTester.testMatch(deserialized, matchingReference) match {
              case SuccessfulMatchTest() =>
                monitorReporters.foreach(_.reportSuccessfulMatch(deserialized))
                successLoopCount += loopCount

              case UnsuccessfulMatchTest(reason, _, _) =>
                monitorReporters.foreach(_.reportFailedMatch(deserialized, matchingReference, reason))
                failureLoopCount += loopCount
            }

          case NoMatchFound() =>
            val headOffset = currentReferenceWindow.headOption.map(_.messageOffset).getOrElse(-1L)
            val tailOffset = currentReferenceWindow.lastOption.map(_.messageOffset).getOrElse(-1L)
            monitorReporters.foreach(_.reportNoMatch(
              deserialized,
              headOffset,
              tailOffset
            ))
            noMatchLoopCount += loopCount

          case IgnoreMessage() =>
            monitorReporters.foreach(_.reportIgnored(deserialized))
            ignoreLoopCount += loopCount
        }
      }

      testMessageProcessed.mark()
    } catch {
      case NonFatal(exception) =>
        logger.error("An exception occured during message handling", exception)
        testMessageErrored.mark()
    } finally {
      lastOffsetProcessed = deserialized.messageOffset
    }
  }

  /**
   * Start this worker.
   */
  def start(): Unit = {
    logger.debug(s"Starting worker ${workerNumber} for ${monitor.identifier}")

    // As messages come in we queue them up in the executor service
    // work queue. They will be handled in the order received, but
    // the consumer is free to continue consuming and handing off
    // things to the worker.
    testConsumer.addCallback { record =>
      val deserialized = messageDeserializationTimer.time {
        testDeserializer.deserialize(
          record.offset(),
          record.partition(),
          record.timestamp(),
          record.key(),
          record.value()
        )
      }

      val taskKey = deserialized.keyInstance match {
        case strKey: String =>
          strKey

        case byteKey: Array[Byte] =>
          val shaDigester = MessageDigest.getInstance("SHA-1")
          val shaBytes = shaDigester.digest(byteKey)
          DatatypeConverter.printHexBinary(shaBytes)

        case objKey: Object =>
          objKey.toString()
      }

      val handler = () => handleMessage(deserialized)
      executor.submit(taskKey, handler)
      testMessageConsumed.mark()
    }

    referenceConsumer.start()
    testConsumer.start()
  }

  /**
   * Stop this worker.
   */
  def stop(): Unit = {
    logger.debug(s"Stopping worker ${workerNumber} for ${monitor.identifier}")

    testConsumer.stop()
    referenceConsumer.stop()
    queueResetterSchedule.cancel(true)

    // Give reporters a bit of time to finish up.
    Thread.sleep(100)
    monitorReporters.foreach(_.close())

    executor.shutdown()
  }
}
