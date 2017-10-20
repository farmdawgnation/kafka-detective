package me.frmr.kafka.detective.matchfinder

import me.frmr.kafka.detective.api._
import org.scalatest._

class KeyEqualsFinderSpec extends FlatSpec with Matchers {
  "KeyEqualsFinder" should "locate string matches in reference window" in new StringContext {
    val result = new KeyEqualsFinder().find(testMessage, referenceWindowWithMatch)

    result.isInstanceOf[FoundMatch] shouldBe true
    result.asInstanceOf[FoundMatch].matchingReferenceMessage.keyInstance shouldBe testMessage.keyInstance
  }

  it should "not match different strings in reference window" in new StringContext {
    val result = new KeyEqualsFinder().find(testMessage, referenceWindowWithoutMatch)

    result.isInstanceOf[NoMatchFound] shouldBe true
  }

  it should "locate byte array matches in reference window" in new ByteArrayContext {
    val result = new KeyEqualsFinder().find(testMessage, referenceWindowWithMatch)

    result.isInstanceOf[FoundMatch] shouldBe true

    val referenceArray = result.asInstanceOf[FoundMatch].matchingReferenceMessage.keyInstance.asInstanceOf[Array[Byte]]
    val testArray = testMessage.keyInstance.asInstanceOf[Array[Byte]]

    referenceArray should contain theSameElementsInOrderAs testArray
  }

  it should "not match different byte arrays in reference window" in new ByteArrayContext {
    val result = new KeyEqualsFinder().find(testMessage, referenceWindowWithoutMatch)

    result.isInstanceOf[NoMatchFound] shouldBe true
  }

  trait StringContext {
    def makeObjectEnvelope(subject: String) = {
      MonitorObjectEnvelope(
        0L,
        0,
        0,
        subject.getClass,
        subject,
        subject.getClass,
        subject
      )
    }

    val testMessage = makeObjectEnvelope("abcd")

    val referenceWindowWithMatch = Seq(
      makeObjectEnvelope("zzzzz"),
      makeObjectEnvelope("12345"),
      makeObjectEnvelope("09999"),
      makeObjectEnvelope("abcd"),
      makeObjectEnvelope("lklklklk")
    )

    val referenceWindowWithoutMatch = Seq(
      makeObjectEnvelope("zzzzz"),
      makeObjectEnvelope("12345"),
      makeObjectEnvelope("09999"),
      makeObjectEnvelope("lklklklk")
    )
  }

  trait ByteArrayContext {
    def makeObjectEnvelope(subject: String) = {
      val byteSubject = subject.getBytes("UTF-8")
      MonitorObjectEnvelope(
        0L,
        0,
        0,
        byteSubject.getClass,
        byteSubject,
        byteSubject.getClass,
        byteSubject
      )
    }

    val testMessage = makeObjectEnvelope("abcd")

    val referenceWindowWithMatch = Seq(
      makeObjectEnvelope("zzzzz"),
      makeObjectEnvelope("12345"),
      makeObjectEnvelope("09999"),
      makeObjectEnvelope("abcd"),
      makeObjectEnvelope("lklklklk")
    )

    val referenceWindowWithoutMatch = Seq(
      makeObjectEnvelope("zzzzz"),
      makeObjectEnvelope("12345"),
      makeObjectEnvelope("09999"),
      makeObjectEnvelope("lklklklk")
    )
  }
}
