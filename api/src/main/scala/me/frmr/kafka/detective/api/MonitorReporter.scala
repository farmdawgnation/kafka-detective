package me.frmr.kafka.detective.api

/**
 * In the Kafka Detective world, a `MonitorReporter` is responsible for reporting match events
 * out to some other system. You could imagine that this is reporting out to... anything, really.
 * It could be a metrics aggregator, it could be another Kafka topic, it could be anything!
 *
 * Instances of this class should have a constructor that takes two arguments:
 *
 *  - `monitorIdentifier: String` - the string identifier for the monitor this reporter instance is for
 *  - `config: java.util.Map[String, Object]` - The `reporter-configs` block from daemon configuration
 */
trait MonitorReporter {
  /**
   * Reports a successful match to a message in the reference topic.
   *
   * @param envelope A copy of the test message instance that was sucessfully matched.
   */
  def reportSuccessfulMatch(envelope: MonitorObjectEnvelope): Unit

  /**
   * Reports an unsuccessful match to a message in the reference topic.
   *
   * @param envelope A copy of the test message instance that should have successfully matched.
   * @param referenceEnvelope A copy of the reference message that this message should have matched.
   */
  def reportFailedMatch(envelope: MonitorObjectEnvelope, referenceEnvelope: MonitorObjectEnvelope, reason: String): Unit

  /**
   * Reports that no match was found in the reference window, and that no further retries will be
   * attempted for matching the message.
   */
  def reportNoMatch(envelope: MonitorObjectEnvelope, referenceWindowStart: Long, referenceWindowEnd: Long): Unit

  /**
   * Reports that a test message was totally ignored by the potential match finding code.
   */
  def reportIgnored(envelope: MonitorObjectEnvelope): Unit

  /**
   * Do any shutdown or resource cleanup for the reporter.
   */
  def close(): Unit
}
