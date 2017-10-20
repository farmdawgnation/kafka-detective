package me.frmr.kafka.detective.monitor

import me.frmr.kafka.detective._
import me.frmr.kafka.detective.api._
import me.frmr.kafka.detective.domain._
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent._

/**
 * Class that oversees all monitoring activity for an individual monitor. The MonitoraWarden
 * will delegate actual monitoring activities to MonitorWorkers.
 *
 * If the monitor isn't limited to certain partitions, Kafka Detective will expect that
 * thw two topics should match identically, with partition n on the test topic matching
 * partition n on the reference topic, for any valid partition number n.
 *
 * If no partition restriction is specified, the warden will create a thread pool of
 * defaultProcessingThreadPoolSize and create MonitorWorkers for each thread. Each of
 * the workers will have its own Consumer. Partition assignment to the reference topic
 * will be managed by Kafka. Partition reassignments to the test topic will be managed
 * using a tandem rebalance listener that ensures the reference and test topic are always assigned
 * to the same partitions.
 *
 * If a partition restriction is specified, the warden will only start one worker which will handle
 * all matching activity for the monitor. For high traffic topics this will work best if each
 * monitor only has one partition on each side to keep track of, though multiple partitions on
 * each side are supported.
 */
class MonitorWarden(
  monitor: Monitor,
  maxWorkerCountPerMonitor: Int,
  comparisonThreadsPerWorker: Int,
  emptyQueuePollIntervalMs: Int,
  queueResetMs: Int
) extends Metrics with StrictLogging {
  private[this] var workers: Seq[MonitorWorker] = Seq()

  def start(): Unit = {
    logger.debug(s"Starting warden for ${monitor.identifier}")

    if (monitor.referenceSubject.onlyPartitions.length != monitor.testSubject.onlyPartitions.length) {
      throw new IllegalStateException("You must define the same number of partitions for test and reference subject")
    }

    val numberOfPartitions = monitor.referenceSubject.onlyPartitions.length
    val workerCount = math.min(maxWorkerCountPerMonitor, numberOfPartitions)
    val partitionsPerWorker = math.ceil(numberOfPartitions.toDouble / workerCount.toDouble).toInt

    logger.debug(s"Warden for ${monitor.identifier} will request ${workerCount} workers")

    for (index <- (0 until workerCount)) {
      val filteredReferencePartitions =
        monitor.referenceSubject.onlyPartitions.drop(index * partitionsPerWorker).take(partitionsPerWorker)
      val filteredTestPartitions =
        monitor.testSubject.onlyPartitions.drop(index * partitionsPerWorker).take(partitionsPerWorker)

      val updatedMonitor = monitor.copy(
        referenceSubject = monitor.referenceSubject.copy(
          onlyPartitions = filteredReferencePartitions
        ),
        testSubject = monitor.testSubject.copy(
          onlyPartitions = filteredTestPartitions
        )
      )

      logger.debug(s"Instantiating worker ${index} for ${monitor.identifier} to handle reference partitions ${filteredReferencePartitions.mkString(",")} and test partitions ${filteredTestPartitions.mkString(",")}")

      val worker = new MonitorWorker(
        updatedMonitor,
        index,
        comparisonThreadsPerWorker,
        emptyQueuePollIntervalMs,
        queueResetMs
      )

      workers = workers :+ worker
    }

    logger.debug(s"Starting workers for ${monitor.identifier}")

    workers.foreach(_.start())
  }

  def stop(): Unit = {
    logger.debug(s"Stopping warden for ${monitor.identifier}")
    workers.foreach(_.stop())
    workers = Seq()
  }
}
