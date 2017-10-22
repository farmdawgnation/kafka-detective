package me.frmr.kafka.detective.monitor

import me.frmr.kafka.detective.domain._
import me.frmr.kafka.detective.deserializer._
import me.frmr.kafka.detective.matchfinder._
import me.frmr.kafka.detective.matchtester._
import me.frmr.kafka.detective.reporter._
import me.frmr.kafka.detective.util._
import java.util.Properties
import org.apache.kafka.clients.producer._
import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.time._
import scala.collection.JavaConverters._
import scala.util.Random

class MonitorWorkerIntegrationSpec extends FlatSpec with Matchers with Eventually {
  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(15, Millis)))

  "MonitorWorker" should "properly process a small number of messages" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSuccesses(10, produceRef, produceTest)
    produceFailures(10, produceRef, produceTest)
    produceMisses(10, produceRef, produceTest)
    Thread.sleep(100)

    eventually {
      withClue("successful counter mismatch") { reporter.successfulCounter.get() shouldBe 10 }
      withClue("failed counter mismatch") { reporter.failedCounter.get() shouldBe 10 }
      withClue("miss counter mismatch") { reporter.missCounter.get() shouldBe 10 }
    }
  }

  it should "wait for more reference messages if a test message is from the future" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSingleSide(20, produceTest)
    Thread.sleep(100)

    withClue("successful counter mismatch") { reporter.successfulCounter.get() shouldBe 0 }
    withClue("failed counter mismatch") { reporter.failedCounter.get() shouldBe 0 }
    withClue("miss counter mismatch") { reporter.missCounter.get() shouldBe 0 }
    withClue("ignored counter mismatch") { reporter.ignoredCounter.get() shouldBe 0 }

    produceSingleSide(20, produceRef)

    eventually {
      val total = reporter.successfulCounter.get() +
        reporter.failedCounter.get() +
        reporter.missCounter.get() +
        reporter.ignoredCounter.get()

      total shouldBe 20
      reporter.ignoredCounter.get() should be < 20
    }
  }

  it should "reset when the test comsumer backpressures for too long" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSingleSide(52, produceTest)
    Thread.sleep(100) // ensure the messages can get consumed

    eventually {
      withClue("miss counter mismatch") { reporter.missCounter.get() shouldBe 0 }
      withClue("failed counter mismatch") { reporter.failedCounter.get() shouldBe 0 }
      withClue("successful counter mismatch") { reporter.successfulCounter.get() shouldBe 0 }
      withClue("ignored counter mismatch") { reporter.ignoredCounter.get() shouldBe 52 }
      worker.resettingWorker shouldBe false
    }
  }

  it should "reset when the reference comsumer backpressures for too long" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSingleSide(52, produceRef)
    Thread.sleep(100) // ensure the messages can get consumed

    eventually {
      worker.referenceConsumer.getCurrentWindow().length shouldBe 0
      worker.resettingWorker shouldBe false
    }
  }

  def produceSingleSide(number: Int, produce: (String, String) => Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val messageValue = Random.alphanumeric.take(32).mkString
      produce(messageKey, messageValue)
    }
  }

  def produceSuccesses(number: Int, produceRef: (String, String)=>Unit, produceTest: (String, String)=>Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val messageValue = Random.alphanumeric.take(32).mkString
      produceRef(messageKey, messageValue)
      produceTest(messageKey, messageValue)
    }
  }

  def produceFailures(number: Int, produceRef: (String, String)=>Unit, produceTest: (String, String)=>Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val message1Value = Random.alphanumeric.take(32).mkString
      val message2Value = Random.alphanumeric.take(32).mkString

      produceRef(messageKey, message1Value)
      produceTest(messageKey, message2Value)
    }
  }

  def produceMisses(number: Int, produceRef: (String, String)=>Unit, produceTest: (String, String)=>Unit): Unit = {
    for (i <- (0 until number)) {
      val message1Key = Random.alphanumeric.take(32).mkString
      val message1Value = Random.alphanumeric.take(32).mkString

      val message2Key = Random.alphanumeric.take(32).mkString
      val message2Value = Random.alphanumeric.take(32).mkString

      produceRef(message1Key, message1Value)
      produceTest(message2Key, message2Value)
    }
  }

  val kafkaHost = sys.env.get("IT_KAFKA_HOST").getOrElse("localhost:9092")
  val kafkaAdmin = new KafkaAdmin(kafkaHost)

  def withFixtures(testCode: ((String,String)=>Unit, (String,String)=>Unit, MonitorWorker, CountingReporter)=>Any) = {
    val countingReporter = new CountingReporter("worker-integration-tests", Map.empty.asJava)

    val referenceTopicName = Random.alphanumeric.take(16).mkString
    val testTopicName = Random.alphanumeric.take(16).mkString

    kafkaAdmin.createTopic(referenceTopicName, 1)
    kafkaAdmin.createTopic(testTopicName, 1)

    val referenceSubject = MonitorSubject(
      kafkaHost,
      referenceTopicName,
      Seq(0),
      () => new StringDeserializer
    )

    val testSubject = MonitorSubject(
      kafkaHost,
      testTopicName,
      Seq(0),
      () => new StringDeserializer
    )

    val monitor = Monitor(
      "worker-integration-tests",
      referenceSubject,
      testSubject,
      () => new KeyEqualsFinder,
      () => new ValueEqualsTester,
      () => Seq(countingReporter),
      referenceWindowSize = 100
    )
    val monitorWorker = new MonitorWorker(
      monitor,
      workerNumber = Random.nextInt(),
      numberOfComparisonThreads = 1,
      emptyQueuePollIntervalMs = 10,
      queueResetMs = 1000
    )

    val props = new Properties()
    props.put("bootstrap.servers", kafkaHost)
    props.put("acks", "all")
    props.put("retries", "0")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    val producer = new KafkaProducer[String, String](props)

    def produceReferenceMessage(key: String, value: String): Unit = {
      val record = new ProducerRecord[String, String](referenceTopicName, key, value)
      producer.send(record)
    }

    def produceTestMessage(key: String, value: String): Unit = {
      val record = new ProducerRecord[String, String](testTopicName, key, value)
      producer.send(record)
    }

    monitorWorker.start()

    try {
      testCode(produceReferenceMessage, produceTestMessage, monitorWorker, countingReporter)
    } finally {
      monitorWorker.stop()
      producer.close()
    }
  }
}
