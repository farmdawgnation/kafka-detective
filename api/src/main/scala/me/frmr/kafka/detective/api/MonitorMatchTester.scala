package me.frmr.kafka.detective.api

/**
 * In the Kafka Detective world, the `MonitorMatchTester` is responsible for determining whether or
 * not a given `testMessage` and a given `referenceMessage` match. At this point the assumption is
 * that these messages '''should''' match because a [[MonitorMatchFinder]] already paired them up.
 * However, the tester has the responsibility of determining if they actually ''do'' match.
 *
 * If you need to do expensive computation or have some level of complexity when determining
 * equivalence, it is ideal to stick that logic inside a [[MonitorMatchTester]], since that code
 * is run many, many fewer times than code defined in a [[MonitorMatchFinder]].
 *
 * A tester can return one of two possible results:
 *
 *  - A [[SuccessfulMatchTest]] result, which indicates that the messages matched.
 *  - An [[UnsuccessfulMatchTest]] result, which indicates they did not match, and contains information helpful for figuring out why.
 *
 * There are a handful of default match testers provided in the
 * `me.frmr.kafka.detective.matchtester` package of the Detective daemon.
 *
 * Instances of this class should have a zero-arg constructor.
 */
trait MonitorMatchTester {
  def testMatch(
    testMessage: MonitorObjectEnvelope,
    referenceMessage: MonitorObjectEnvelope
  ): MonitorMatchTestResult
}

/**
 * A type indicating the result of a match test.
 */
sealed trait MonitorMatchTestResult

/**
 * A match test result type indicating the match test was successful and that the provided
 * `testMessage` and `referenceMessage` actually do match.
 */
case class SuccessfulMatchTest() extends MonitorMatchTestResult

/**
 * A match test result type indicating the match test was unsuccessful and that the provided
 * `testMessage` and `referenceMessage` do not match.
 *
 * @param reason A string reason indicating why the match failed
 * @param testMessage The test message from the comparison that failed
 * @param referenceMessage The reference message from the comparison that failed
 */
case class UnsuccessfulMatchTest(
  reason: String,
  testMessage: MonitorObjectEnvelope,
  referenceMessage: MonitorObjectEnvelope
) extends MonitorMatchTestResult
