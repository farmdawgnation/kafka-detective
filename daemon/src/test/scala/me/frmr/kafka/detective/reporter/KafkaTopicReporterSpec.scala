package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api._
import org.apache.kafka.clients.producer._
import org.scalatest._
import scala.collection.JavaConverters._
import java.util.{Map => JMap}

class KafkaTopicReporterSpec extends FlatSpec with Matchers {
  "KafkaTopicReporter" should "output on failed matches if failed reporting enabled" in withFixtures() { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"monitor_identifier":"unit-test","result":"failed","reason":"Values didn't match","test_message":{"key":"bacon","value":"apple","partition":0,"offset":0},"reference_message":{"key":"bacon","value":"apple222","partition":0,"offset":0}}"""
    reporter.reportFailedMatch(exampleEnvelope, exampleEnvelope2, "Values didn't match")

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  it should "not output on failed matches if failed reporting disabled" in withFixtures(reportFailed = "false") { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    reporter.reportFailedMatch(exampleEnvelope, exampleEnvelope2, "Values didn't match")
    mockProducer.history().size shouldBe 0
  }

  it should "output on no matches if no match reporting enabled" in withFixtures() { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"monitor_identifier":"unit-test","result":"no-match","reference_window_start":-1,"reference_window_end":-1,"test_message":{"key":"bacon","value":"apple","partition":0,"offset":0}}"""
    reporter.reportNoMatch(exampleEnvelope, -1L, -1L)

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  it should "not output on no matches if no match reporting disabled" in withFixtures(reportKeyMiss = "false") { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    reporter.reportNoMatch(exampleEnvelope, -1L, -1L)
    mockProducer.history().size shouldBe 0
  }

  it should "not output anything on successful matches if successful match reporting disabled" in withFixtures() { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    reporter.reportSuccessfulMatch(exampleEnvelope)
    mockProducer.history().size shouldBe 0
  }

  it should "output on successful matches if successful match reporting enabled" in withFixtures(reportSuccess = "true") { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"monitor_identifier":"unit-test","result":"success","test_message":{"key":"bacon","value":"apple","partition":0,"offset":0}}"""
    reporter.reportSuccessfulMatch(exampleEnvelope)

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  it should "not output anything on ignored matches if ignored match reporting disabled" in withFixtures() { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    reporter.reportIgnored(exampleEnvelope)
    mockProducer.history().size shouldBe 0
  }

  it should "output on ignored matches if ignored match reporting enabled" in withFixtures(reportIgnored = "true") { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"monitor_identifier":"unit-test","result":"ignored","test_message":{"key":"bacon","value":"apple","partition":0,"offset":0}}"""
    reporter.reportIgnored(exampleEnvelope)

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  it should "add extra metadata to failed match when provided" in withFixtures(Map("index" -> "my_awesome_index")) { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"index":"my_awesome_index","monitor_identifier":"unit-test","result":"failed","reason":"Values didn't match","test_message":{"key":"bacon","value":"apple","partition":0,"offset":0},"reference_message":{"key":"bacon","value":"apple222","partition":0,"offset":0}}"""
    reporter.reportFailedMatch(exampleEnvelope, exampleEnvelope2, "Values didn't match")

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  it should "add extra metadata to no-match when provided" in withFixtures(Map("index" -> "my_awesome_index")) { (mockProducer, reporter, exampleEnvelope, exampleEnvelope2) =>
    val expectedJson = """{"index":"my_awesome_index","monitor_identifier":"unit-test","result":"no-match","reference_window_start":-1,"reference_window_end":-1,"test_message":{"key":"bacon","value":"apple","partition":0,"offset":0}}"""
    reporter.reportNoMatch(exampleEnvelope, -1L, -1L)

    mockProducer.history().size shouldBe 1

    val actualJson = new String(mockProducer.history().get(0).value(), "UTF-8")
    actualJson shouldBe expectedJson
  }

  def withFixtures(
    metadata: Map[String, Object] = Map.empty,
    reportFailed: String = "default",
    reportKeyMiss: String = "default",
    reportSuccess: String = "default",
    reportIgnored: String = "default"
  )(test: (MockProducer[Array[Byte], Array[Byte]], KafkaTopicReporter, MonitorObjectEnvelope, MonitorObjectEnvelope)=>Any) {
    val mockProducer = new MockProducer(
      true,
      new org.apache.kafka.common.serialization.ByteArraySerializer(),
      new org.apache.kafka.common.serialization.ByteArraySerializer()
    )

    val properties: JMap[String, Object] =
      Map(
        "kafka" -> Map(
          "topic" -> "unit-test",
          "producer" -> Map().asJava,
          "report-success" -> reportSuccess,
          "report-failed" -> reportFailed,
          "report-key-miss" -> reportKeyMiss,
          "report-ignored" -> reportIgnored,
          "extra-metadata" -> metadata.asJava
        ).asJava
      ).asJava
      .asInstanceOf[JMap[String,Object]]

    val reporter = new KafkaTopicReporter(
      "unit-test",
      properties
    ) {
      override lazy val producer = mockProducer
    }

    val exampleEnvelope = MonitorObjectEnvelope(
      0L,
      0,
      0L,
      classOf[String],
      "bacon",
      classOf[String],
      "apple"
    )

    val exampleEnvelope2 = MonitorObjectEnvelope(
      0L,
      0,
      0L,
      classOf[String],
      "bacon",
      classOf[String],
      "apple222"
    )

    test(mockProducer, reporter, exampleEnvelope, exampleEnvelope2)
  }
}
