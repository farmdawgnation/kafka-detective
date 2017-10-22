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

class MultiPartitionMonitorWorkerIntegrationSpec extends FlatSpec with Matchers with Eventually {
  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(15, Millis)))

  "MonitorWorker" should "properly process a small number of messages" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSuccesses(10, produceRef, produceTest)
    produceFailures(10, produceRef, produceTest)
    produceMisses(5, produceRef, produceTest, 0)
    produceMisses(5, produceRef, produceTest, 1)

    eventually {
      withClue("successful counter mismatch") { reporter.successfulCounter.get() shouldBe 10 }
      withClue("failed counter mismatch") { reporter.failedCounter.get() shouldBe 10 }
      withClue("miss counter mismatch") { reporter.missCounter.get() shouldBe 10 }
    }
  }

  it should "wait for more reference messages if a test message is from the future" in withFixtures { (produceRef, produceTest, worker, reporter) =>
    produceSingleSide(20, produceTest)

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
    produceSingleSide(55, produceRef)
    Thread.sleep(100) // ensure the messages can get consumed

    eventually {
      worker.referenceConsumer.getCurrentWindow().length shouldBe 0
    }
  }

  def produceSingleSide(number: Int, produce: (String, String,Option[Int]) => Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val messageValue = Random.alphanumeric.take(32).mkString
      produce(messageKey, messageValue, Some(0))
    }
  }

  def produceSuccesses(number: Int, produceRef: (String, String,Option[Int])=>Unit, produceTest: (String, String,Option[Int])=>Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val messageValue = Random.alphanumeric.take(32).mkString
      produceRef(messageKey, messageValue, None)
      produceTest(messageKey, messageValue, None)
    }
  }

  def produceFailures(number: Int, produceRef: (String, String,Option[Int])=>Unit, produceTest: (String, String,Option[Int])=>Unit): Unit = {
    for (i <- (0 until number)) {
      val messageKey = Random.alphanumeric.take(32).mkString
      val message1Value = Random.alphanumeric.take(32).mkString
      val message2Value = Random.alphanumeric.take(32).mkString

      produceRef(messageKey, message1Value, None)
      produceTest(messageKey, message2Value, None)
    }
  }

  def produceMisses(number: Int, produceRef: (String, String,Option[Int])=>Unit, produceTest: (String, String,Option[Int])=>Unit, partition: Int): Unit = {
    for (i <- (0 until number)) {
      val message1Key = Random.alphanumeric.take(32).mkString
      val message1Value = Random.alphanumeric.take(32).mkString

      val message2Key = Random.alphanumeric.take(32).mkString
      val message2Value = Random.alphanumeric.take(32).mkString

      produceRef(message1Key, message1Value, Some(partition))
      produceTest(message2Key, message2Value, Some(partition))
    }
  }

  val kafkaHost = sys.env.get("IT_KAFKA_HOST").getOrElse("localhost:9092")
  val kafkaAdmin = new KafkaAdmin(kafkaHost)

  def withFixtures(testCode: ((String,String,Option[Int])=>Unit, (String,String,Option[Int])=>Unit, MonitorWorker, CountingReporter)=>Any) = {
    val countingReporter = new CountingReporter("worker-integration-tests", Map.empty.asJava)

    val referenceTopicName = Random.alphanumeric.take(16).mkString
    val testTopicName = Random.alphanumeric.take(16).mkString

    kafkaAdmin.createTopic(referenceTopicName, 2)
    kafkaAdmin.createTopic(testTopicName, 2)

    val referenceSubject = MonitorSubject(
      kafkaHost,
      referenceTopicName,
      Seq(0, 1),
      () => new StringDeserializer
    )

    val testSubject = MonitorSubject(
      kafkaHost,
      testTopicName,
      Seq(0, 1),
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

    def produceReferenceMessage(key: String, value: String, partition: Option[Int] = None): Unit = {
      val actualPartition = partition getOrElse key.chars().iterator.asScala.foldLeft(0)(_ + _) % 2
      val record = new ProducerRecord[String, String](referenceTopicName, actualPartition, key, value)
      producer.send(record)
    }

    def produceTestMessage(key: String, value: String, partition: Option[Int] = None): Unit = {
      val actualPartition = partition getOrElse key.chars().iterator.asScala.foldLeft(0)(_ + _) % 2
      val record = new ProducerRecord[String, String](testTopicName, actualPartition, key, value)
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
