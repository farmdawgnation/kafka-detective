package me.frmr.kafka.detective.domain

import me.frmr.kafka.detective.api._

/**
 * Represents a Kafka server and topic being monitored. Might limit the monitor to only some
 * partitions of the topic.
 */
private[detective] case class MonitorSubject(
  bootstrapServer: String,
  topic: String,
  onlyPartitions: Seq[Int],
  deserializer: () => MonitorDeserializer
)

/**
 * Represents some set of Kafka topics and partitions that are having their traffic monitored.
 */
private[detective] case class Monitor(
  identifier: String,
  referenceSubject: MonitorSubject,
  testSubject: MonitorSubject,
  matchFinder: () => MonitorMatchFinder,
  matchTester: () => MonitorMatchTester,
  reporters: () => Seq[MonitorReporter],
  referenceWindowSize: Int
)
