package me.frmr.kafka.detective

import java.util.Properties
import org.apache.kafka.common._
import org.apache.kafka.clients.consumer._
import org.scalatest._
import org.scalatest.concurrent._
import scala.collection.JavaConverters._

class CallbackKafkaConsumerSpec extends FlatSpec with Matchers with Eventually {
  "CallbackKafkaConsumer" should "init with running and hasBeenUsed as false" in withFixtures { (mockConsumer, callbackConsumer) =>
    callbackConsumer.getRunning shouldBe false
    callbackConsumer.getHasBeenUsed shouldBe false
  }

  it should "invoke the callback when it receives records" in withFixtures { (mockConsumer, callbackConsumer) =>
    @volatile var sawMessage: Option[(String, String)] = None

    callbackConsumer.doAssignments()
    mockConsumer.addRecord(newRecord("abc", "def"))

    callbackConsumer.addCallback { (record) =>
      sawMessage = Some((record.key(), record.value()))
    }

    callbackConsumer.start()

    eventually {
      sawMessage shouldBe Some(("abc", "def"))
    }
  }

  it should "invoke the batch callback when it receives records" in withFixtures { (mockConsumer, callbackConsumer) =>
    @volatile var sawMessage: Option[(String, String)] = None

    callbackConsumer.doAssignments()
    mockConsumer.addRecord(newRecord("abc", "def"))

    callbackConsumer.addBatchCallback { (records) =>
      records.asScala.toList.foreach { record =>
        sawMessage = Some((record.key(), record.value()))
      }
    }

    callbackConsumer.start()

    eventually {
      sawMessage shouldBe Some(("abc", "def"))
    }
  }

  it should "report itself as not running and used after stop" in withFixtures { (mockConsumer, callbackConsumer) =>
    callbackConsumer.start()
    callbackConsumer.stop()

    eventually {
      callbackConsumer.getRunning shouldBe false
      callbackConsumer.getHasBeenUsed shouldBe true
    }
  }

  it should "throw an exception if you attempt to use it more than once" in withFixtures { (mockConsumer, callbackConsumer) =>
    callbackConsumer.start()
    Thread.sleep(5)

    val thrown = the [RuntimeException] thrownBy callbackConsumer.start()

    thrown.getMessage should equal ("This callback kafka consumer has already been used. Please instantiate a new one.")
  }

  it should "properly handle a reset request" in withFixtures { (mockConsumer, callbackConsumer) =>
    @volatile var sawReset: Boolean = false
    @volatile var sawMessage: Option[(String, String)] = None

    callbackConsumer.addBatchCallback { (records) =>
      records.asScala.toList.foreach { record =>
        sawMessage = Some((record.key(), record.value()))
      }
    }

    callbackConsumer.doAssignments()
    mockConsumer.addRecord(newRecord("abc", "def"))

    callbackConsumer.addResetCallback( () => sawReset = true )

    callbackConsumer.start()

    eventually {
      withClue("Didn't consume message") {
        callbackConsumer.getPositionAheadOfStart should be > 0L
      }
    }

    callbackConsumer.reset()

    eventually {
      withClue("Didn't see reset flag flip") {
        sawReset shouldBe true
      }
      sawMessage shouldBe Some(("abc", "def"))
      callbackConsumer.getPositionAheadOfStart shouldBe 0L
    }
  }

  it should "give reset priority over backpressure" in withFixtures { (mockConsumer, callbackConsumer) =>
    @volatile var sawReset: Boolean = false
    callbackConsumer.addResetCallback( () => sawReset = true )
    callbackConsumer.setBackpressureFn( Some(() => true) )

    callbackConsumer.start()
    callbackConsumer.reset()

    eventually {
      withClue("Didn't see reset flag flip") {
        sawReset shouldBe true
      }
      withClue("Didn't see backpressure flag flip") {
        callbackConsumer.isBackpressuring shouldBe true
      }
    }
  }

  var msgOffset = 0L
  def newRecord(key: String, value: String): ConsumerRecord[String, String] = {
    val newOffset = msgOffset + 1L
    msgOffset = newOffset
    new ConsumerRecord("test", 0, msgOffset, key, value)
  }

  def withFixtures(testCode: (MockConsumer[String, String], CallbackKafkaConsumer[String, String])=>Any) = {
    val mockConsumer = new MockConsumer[String, String](OffsetResetStrategy.EARLIEST)

    val windowingConsumer = new CallbackKafkaConsumer[String, String]("test", Seq(0), new Properties()) {
      override lazy val consumer: Consumer[String, String] = mockConsumer

      override def doAssignments: Unit = {
        mockConsumer.assign(Seq(new TopicPartition("test", 0)).asJava)
        mockConsumer.updateBeginningOffsets(Map(new TopicPartition("test", 0) -> new java.lang.Long(0)).asJava)
      }
    }

    try {
      testCode(mockConsumer, windowingConsumer)
    } finally {
      windowingConsumer.stop()
    }
  }
}
