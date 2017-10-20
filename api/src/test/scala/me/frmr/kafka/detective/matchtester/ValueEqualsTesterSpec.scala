package me.frmr.kafka.detective.matchtester

import me.frmr.kafka.detective.api._
import org.scalatest._

class ValueEqualsTesterSpec extends FlatSpec with Matchers {
  "ValueEqualsTester" should "find equivalent strings" in {
    val arbitraryString1 = "abcdef"
    val testEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryString1.getClass,
      arbitraryString1,
      arbitraryString1.getClass,
      arbitraryString1
    )

    val arbitraryString2 = "abcdef"
    val referenceEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryString2.getClass,
      arbitraryString2,
      arbitraryString2.getClass,
      arbitraryString2
    )

    val matchResult = new ValueEqualsTester().testMatch(testEnvelope, referenceEnvelope)

    matchResult shouldBe SuccessfulMatchTest()
  }

  it should "flag non-euqivalent strings" in {
    val arbitraryString1 = "abcdef"
    val testEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryString1.getClass,
      arbitraryString1,
      arbitraryString1.getClass,
      arbitraryString1
    )

    val arbitraryString2 = "wxyz"
    val referenceEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryString2.getClass,
      arbitraryString2,
      arbitraryString2.getClass,
      arbitraryString2
    )

    val matchResult = new ValueEqualsTester().testMatch(testEnvelope, referenceEnvelope)

    matchResult shouldBe UnsuccessfulMatchTest("Object.equals returned false", testEnvelope, referenceEnvelope)
  }

  it should "find equivalent byte arrays" in {
    val arbitraryBytes1 = "abcdef".getBytes("UTF-8")
    val testEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryBytes1.getClass,
      arbitraryBytes1,
      arbitraryBytes1.getClass,
      arbitraryBytes1
    )

    val arbitraryBytes2 = "abcdef".getBytes("UTF-8")
    val referenceEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryBytes2.getClass,
      arbitraryBytes2,
      arbitraryBytes2.getClass,
      arbitraryBytes2
    )

    val matchResult = new ValueEqualsTester().testMatch(testEnvelope, referenceEnvelope)

    matchResult shouldBe SuccessfulMatchTest()
  }

  it should "flag non-equivalent byte arrays" in {
    val arbitraryBytes1 = "abcdef".getBytes("UTF-8")
    val testEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryBytes1.getClass,
      arbitraryBytes1,
      arbitraryBytes1.getClass,
      arbitraryBytes1
    )

    val arbitraryBytes2 = "wxyz".getBytes("UTF-8")
    val referenceEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0,
      arbitraryBytes2.getClass,
      arbitraryBytes2,
      arbitraryBytes2.getClass,
      arbitraryBytes2
    )

    val matchResult = new ValueEqualsTester().testMatch(testEnvelope, referenceEnvelope)

    matchResult shouldBe UnsuccessfulMatchTest("Object.equals returned false", testEnvelope, referenceEnvelope)
  }
}
