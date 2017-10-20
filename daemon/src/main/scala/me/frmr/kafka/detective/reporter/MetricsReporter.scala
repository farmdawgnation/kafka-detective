package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective._
import me.frmr.kafka.detective.api._
import com.typesafe.scalalogging.StrictLogging
import java.util.{Map => JMap}
import java.util.concurrent.ConcurrentHashMap
import nl.grons.metrics.scala._

object MetricsReporter extends Metrics {
  private[this] val metricsStore: ConcurrentHashMap[String, Meter] = new ConcurrentHashMap()

  def meterFor(identifier: String): Meter = {
    if (metricsStore.containsKey(identifier)) {
      metricsStore.get(identifier)
    } else {
      val newMeter = metrics.meter("reporter." + identifier)
      metricsStore.put(identifier, newMeter)
      newMeter
    }
  }
}
class MetricsReporter(monitorIdentifier: String, config: JMap[String, Object]) extends MonitorReporter with StrictLogging {
  import MetricsReporter._

  logger.info(s"Metrics reporting started for $monitorIdentifier")

  def reportSuccessfulMatch(envelope: MonitorObjectEnvelope): Unit = {
    meterFor(s"${monitorIdentifier}.successful-match").mark()
  }
  def reportFailedMatch(envelope: MonitorObjectEnvelope, referenceEnvelope: MonitorObjectEnvelope, reason: String): Unit = {
    meterFor(s"${monitorIdentifier}.failed-match").mark()
  }
  def reportNoMatch(envelope: MonitorObjectEnvelope, referenceWindowStart: Long, referenceWindowEnd: Long): Unit = {
    meterFor(s"${monitorIdentifier}.no-match").mark()
  }
  def reportIgnored(envelope: MonitorObjectEnvelope): Unit = {
    meterFor(s"${monitorIdentifier}.ignored").mark()
  }

  def close(): Unit = ()
}
