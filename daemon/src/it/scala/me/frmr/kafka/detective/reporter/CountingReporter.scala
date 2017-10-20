package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api._
import java.util.concurrent.atomic._
import java.util.{Map => JMap}

class CountingReporter(monitorIdentifier: String, config: JMap[String, Object]) extends MonitorReporter {
  val successfulCounter: AtomicInteger = new AtomicInteger(0)
  val failedCounter: AtomicInteger = new AtomicInteger(0)
  val missCounter: AtomicInteger = new AtomicInteger(0)
  val ignoredCounter: AtomicInteger = new AtomicInteger(0)

  def reportSuccessfulMatch(e: MonitorObjectEnvelope): Unit = successfulCounter.incrementAndGet()
  def reportFailedMatch(e: MonitorObjectEnvelope, r: MonitorObjectEnvelope, reason: String): Unit = failedCounter.incrementAndGet()
  def reportNoMatch(e: MonitorObjectEnvelope, windowStart: Long, windowEnd: Long): Unit = missCounter.incrementAndGet()
  def reportIgnored(e: MonitorObjectEnvelope): Unit = ignoredCounter.incrementAndGet()

  def close(): Unit = ()
}
