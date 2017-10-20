package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api._
import com.typesafe.scalalogging.StrictLogging
import java.util.{Map => JMap}

class LoggingReporter(monitorIdentifier: String, config: JMap[String, Object]) extends MonitorReporter with StrictLogging {
  logger.info(s"Logging reporter started for $monitorIdentifier")

  def reportSuccessfulMatch(envelope: MonitorObjectEnvelope): Unit = {
    logger.info(s"Message at offset ${envelope.messageOffset} successfully matched.")
  }
  def reportFailedMatch(envelope: MonitorObjectEnvelope, referenceEnvelope: MonitorObjectEnvelope, reason: String): Unit = {
    logger.info(s"Message at offset ${envelope.messageOffset} didn't match ${referenceEnvelope.messageOffset} due to: $reason")
  }
  def reportNoMatch(envelope: MonitorObjectEnvelope, referenceWindowStart: Long, referenceWindowEnd: Long): Unit = {
    logger.trace(s"No match was found for message at offset ${envelope.messageOffset}")
  }
  def reportIgnored(envelope: MonitorObjectEnvelope): Unit = {
    logger.debug(s"Message at offset ${envelope.messageOffset} was ignored.")
  }

  def close(): Unit = ()
}
