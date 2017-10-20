package me.frmr.kafka.detective

import me.frmr.kafka.detective.deserializer._
import java.util.{Properties, Collection => JCollection}
import org.apache.kafka.common._
import org.apache.kafka.clients.consumer._
import org.scalatest._
import org.scalatest.concurrent._
import scala.collection.JavaConverters._
import scala.util.Random

class WindowingKafkaConsumerSpec extends FlatSpec with Matchers with Eventually {
  "WindowingKafkaConsumer" should "init with running and hasBeenUsed as false" in withFixtures { (mockConsumer, windowingConsumer) =>
    windowingConsumer.getRunning shouldBe false
    windowingConsumer.getHasBeenUsed shouldBe false
  }

  it should "correctly consume and surface a single record" in withFixtures { (mockConsumer, windowingConsumer) =>
    windowingConsumer.doAssignments()

    mockConsumer.addRecord(newRecord("abc", "def"))

    Thread.sleep(1000)

    windowingConsumer.start()

    eventually {
      val recordTuple = windowingConsumer.getCurrentWindow.map { record =>
        (record.keyInstance, record.valueInstance)
      }

      recordTuple shouldBe Seq(("abc", "def"))
    }
  }

  it should "correctly window when receiving more messages" in withFixtures { (mockConsumer, windowingConsumer) =>
    windowingConsumer.doAssignments()
    mockConsumer.addRecord(newRecord("abc", "def"))
    mockConsumer.addRecord(newRecord("xyz", "www"))
    mockConsumer.addRecord(newRecord("123", "444"))
    mockConsumer.addRecord(newRecord("555", "333"))
    mockConsumer.addRecord(newRecord("999", "nnn"))
    mockConsumer.addRecord(newRecord("lll", "asd"))

    windowingConsumer.start()

    eventually {
      val recordTuples = windowingConsumer.getCurrentWindow.map { record =>
        (record.keyInstance, record.valueInstance)
      }

      recordTuples shouldBe Seq(
        ("xyz", "www"),
        ("123", "444"),
        ("555", "333"),
        ("999", "nnn"),
        ("lll", "asd")
      )
    }
  }

  it should "report itself as not running and used after stop" in withFixtures { (mockConsumer, windowingConsumer) =>
    windowingConsumer.start()
    windowingConsumer.stop()

    eventually {
      windowingConsumer.getRunning shouldBe false
      windowingConsumer.getHasBeenUsed shouldBe true
    }
  }

  it should "throw an exception if you attempt to use it more than once" in withFixtures { (mockConsumer, windowingConsumer) =>
    windowingConsumer.start()
    Thread.sleep(5)

    val thrown = the [RuntimeException] thrownBy windowingConsumer.start()

    thrown.getMessage should equal ("This windowing kafka consumer has already been used. Please instantiate a new one.")
  }

  var msgOffset = 0L
  def newRecord(key: String, value: String): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val newOffset = msgOffset + 1L
    msgOffset = newOffset
    val keyBytes = key.getBytes("UTF-8")
    val valueBytes = value.getBytes("UTF-8")
    new ConsumerRecord("test", 0, msgOffset, keyBytes, valueBytes)
  }

  def withFixtures(testCode: (MockConsumer[Array[Byte], Array[Byte]], WindowingKafkaConsumer)=>Any) = {
    val mockConsumer = new MockConsumer[Array[Byte], Array[Byte]](OffsetResetStrategy.EARLIEST)

    val windowingConsumer = new WindowingKafkaConsumer(5, "test", Seq(0), new Properties(), new StringDeserializer(), Random.alphanumeric.take(5).mkString) {
      override lazy val callbackConsumer = new CallbackKafkaConsumer[Array[Byte], Array[Byte]]("test", Seq(0), new Properties()) {
        override lazy val consumer: Consumer[Array[Byte], Array[Byte]] = mockConsumer

        override def doAssignments(): Unit = {
          mockConsumer.assign(Seq(new TopicPartition("test", 0)).asJava)
          mockConsumer.updateBeginningOffsets(Map(new TopicPartition("test", 0) -> new java.lang.Long(0)).asJava)
        }
      }
    }

    try {
      testCode(mockConsumer, windowingConsumer)
    } finally {
      windowingConsumer.stop()
    }
  }
}
