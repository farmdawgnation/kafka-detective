package me.frmr.kafka.detective.api

/**
 * In the Kafka Detective world, a `MonitorMatchFinder` is the interface responsible for determining
 * what mesages in the given `referenceWindow` should be a match for the given `testMessage`. The
 * match finder provides a `find` method that can return any one of a few different results.
 *
 * Specifically, those results are:
 *
 *  - A [[FoundMatch]] instance if a message that should match is found in the `referenceWindow`
 *  - A [[NoMatchFound]] instance if there should have been a message that would match, but there wasn't
 *  - An [[IgnoreMessage]] instance if, based on the key, we chose not to try to find a match
 *
 * Each of these situations will be reported out to the various configured [[MonitorReporter]]s by
 * Detective.
 *
 * Instances of this class should have a zero-arg constructor.
 */
trait MonitorMatchFinder {
  /**
   * Looks for a reference message in the provided `referenceWindow` that should match the given
   * `testMessage`.
   *
   * @param testMessage The message that came from the test topic
   * @param referenceWindow The window of messages available for consideration from the reference topic
   * @return A [[MonitorMatchFindResult]] based on whether or not a match was found.
   */
  def find(
    testMessage: MonitorObjectEnvelope,
    referenceWindow: Seq[MonitorObjectEnvelope]
  ): MonitorMatchFindResult
}

/**
 * The common parent for any kind of result from a [[MonitorMatchFinder]]
 */
sealed trait MonitorMatchFindResult

/**
 * A result from a [[MonitorMatchFinder]] indicating that a reference message that should
 * match was located.
 *
 * @param matchingReferenceMessage The message that should match the test message that was provided
 */
case class FoundMatch(matchingReferenceMessage: MonitorObjectEnvelope) extends MonitorMatchFindResult

/**
 * A result from a [[MonitorMatchFinder]] indicating that no reference message that should match
 * the provided test message was found in the reference window.
 */
case class NoMatchFound() extends MonitorMatchFindResult

/**
 * A result from a [[MonitorMatchFinder]] indicating that, based on the information available,
 * the provided test message should be ignored.
 */
case class IgnoreMessage() extends MonitorMatchFindResult
