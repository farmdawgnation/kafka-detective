package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api._
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonDSL._
import org.apache.kafka.clients.producer._
import java.lang.{Boolean => JBoolean}
import java.util.{Map => JMap, Properties}
import scala.collection.JavaConverters._

/**
 * A reporter that will report match failures and key misses to a Kafka topic.
 *
 * This class requires configuration. The config object will need to provide:
 *
 * "topic" - The topic to publish the no matches and failures to.
 * "producer" - The producer config, particularly the bootstrap servers and any other settings you need
 *
 * @param monitorIdentifier The identifier of the monitor this reporter is configured for
 * @param config The configuration block from the config file
 */
class KafkaTopicReporter(monitorIdentifier: String, config: JMap[String, Object]) extends MonitorReporter with StrictLogging {
  implicit val formats = DefaultFormats

  val kafkaConfig: JMap[String, Object] = {
    config.get("kafka") match {
      case mapObj: JMap[String, Object] =>
        mapObj
      case bacon =>
        throw new RuntimeException("No kafka config was provided in reporter-configs block")
    }
  }

  val topic: String = {
    kafkaConfig.get("topic") match {
      case topicStr: String =>
        topicStr
      case _ =>
        throw new RuntimeException("No topic was provided in kafka reporter configuration")
    }
  }

  val keyTransformer: MonitorReporterTransformer = {
    kafkaConfig.get("key-transformer") match {
      case keyTransformerClass: String =>
        Class.forName(keyTransformerClass).newInstance().asInstanceOf[MonitorReporterTransformer]

      case _ =>
        IdentityTransformer
    }
  }

  val valueTransformer: MonitorReporterTransformer = {
    kafkaConfig.get("value-transformer") match {
      case valueTransformerClass: String =>
        Class.forName(valueTransformerClass).newInstance().asInstanceOf[MonitorReporterTransformer]

      case _ =>
        IdentityTransformer
    }
  }

  private[this] def getBooleanConfig(name: String, default: Boolean): Boolean = {
    kafkaConfig.get(name) match {
      case strVal: String if strVal.toLowerCase == "true" =>
        true

      case strVal: String if strVal.toLowerCase == "false" =>
        false

      case boolVal: JBoolean =>
        boolVal

      case _ =>
        default
    }
  }

  val reportSuccess: Boolean = getBooleanConfig("report-success", default = false)
  val reportIgnored: Boolean = getBooleanConfig("report-ignored", default = false)
  val reportKeyMiss: Boolean = getBooleanConfig("report-key-miss", default = true)
  val reportFailed: Boolean = getBooleanConfig("report-failed", default = true)

  val extraMetadata: JObject = {
    kafkaConfig.get("extra-metadata") match {
      case metadataMap: JMap[String, Object] =>
        val fields = metadataMap.asScala.map({
          case (key, value) =>
            JField(key, decompose(value))
        }).toList

        JObject(fields)

      case _ =>
        JObject()
    }
  }

  val producerProperties = {
    val configProperties: JMap[String, Object] = kafkaConfig.get("producer")
      .asInstanceOf[JMap[String, Object]]

    val producerProperties = new Properties()
    producerProperties.putAll(configProperties)
    producerProperties.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
    producerProperties.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")

    producerProperties
  }

  lazy val producer: Producer[Array[Byte], Array[Byte]] = new KafkaProducer(producerProperties)

  logger.info(s"Kafka topic reporter initilized for $monitorIdentifier outputting to $topic")

  protected def reportResult(
    result: String,
    testMessage: MonitorObjectEnvelope,
    referenceMessage: Option[MonitorObjectEnvelope] = None,
    reason: Option[String] = None,
    referenceWindowStart: Option[Long] = None,
    referenceWindowEnd: Option[Long] = None
  ): Unit = {
    val output: JObject =
      extraMetadata ~
      ("monitor_identifier" -> monitorIdentifier) ~
      ("result" -> result) ~
      ("reference_window_start" -> referenceWindowStart) ~
      ("reference_window_end" -> referenceWindowEnd) ~
      ("reason" -> reason) ~
      ("test_message" -> (
        ("key" -> decompose(keyTransformer.transform(testMessage.keyInstance))) ~
        ("value" -> decompose(valueTransformer.transform(testMessage.valueInstance))) ~
        ("partition" -> testMessage.messagePartition) ~
        ("offset" -> testMessage.messageOffset)
      )) ~
      ("reference_message" -> referenceMessage.map { referenceMessage => (
        ("key" -> decompose(keyTransformer.transform(referenceMessage.keyInstance))) ~
        ("value" -> decompose(valueTransformer.transform(referenceMessage.valueInstance))) ~
        ("partition" -> referenceMessage.messagePartition) ~
        ("offset" -> referenceMessage.messageOffset)
      )})

    val outputMessageBytes = compactRender(output).getBytes("UTF-8")
    val record = new ProducerRecord[Array[Byte], Array[Byte]](topic, outputMessageBytes)
    producer.send(record)
  }

  def reportSuccessfulMatch(envelope: MonitorObjectEnvelope): Unit = {
    if (reportSuccess) {
      reportResult("success", envelope)
    }
  }

  def reportIgnored(envelope: MonitorObjectEnvelope): Unit = {
    if (reportIgnored) {
      reportResult("ignored", envelope)
    }
  }

  def reportFailedMatch(envelope: MonitorObjectEnvelope, referenceEnvelope: MonitorObjectEnvelope, reason: String): Unit = {
    if (reportFailed) {
      reportResult("failed", envelope, Some(referenceEnvelope), Some(reason))
    }
  }

  def reportNoMatch(envelope: MonitorObjectEnvelope, referenceWindowStart: Long, referenceWindowEnd: Long): Unit = {
    if (reportKeyMiss) {
      reportResult("no-match", envelope, referenceWindowStart = Some(referenceWindowStart), referenceWindowEnd = Some(referenceWindowEnd))
    }
  }

  def close(): Unit = {
    producer.close()
  }
}
