package me.frmr.kafka.testtool

import java.util.Properties
import org.apache.kafka.clients.producer._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object SimulationExecutor {
  def newProducer(bootstrapServer: String): KafkaProducer[Array[Byte], Array[Byte]] = {
    val props = new Properties();
    props.put("bootstrap.servers", bootstrapServer);
    props.put("acks", "all");
    props.put("retries", "10");
    props.put("max.in.flight.requests.per.connection", "1");
    props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

    new KafkaProducer[Array[Byte], Array[Byte]](props);
  }
  def executeSimulation(
    bootstrapServer: String,
    topic: String,
    partition: Option[Int],
    batches: Seq[MessageBatch]
  ): Unit = {
    val producer = newProducer(bootstrapServer)

    for (batch <- batches) {
      Thread.sleep(batch.delayMs)

      for ((key, value) <- batch.messages) {
        val record = if (partition.isDefined) {
          new ProducerRecord(topic, partition.get, key, value)
        } else {
          new ProducerRecord(topic, key, value)
        }
        producer.send(record)
      }
    }

    producer.close()
  }

  def execute(
    bootstrapServer: String,
    topic1: String,
    topic1Partition: Option[Int],
    topic2: String,
    topic2Partition: Option[Int],
    topic1Batches: Seq[MessageBatch],
    topic2Batches: Seq[MessageBatch]
  ): Unit = {
    val topic1Sim = Future {
      executeSimulation(
        bootstrapServer,
        topic1,
        topic1Partition,
        topic1Batches
      )
    }

    val topic2Sim = Future {
      executeSimulation(
        bootstrapServer,
        topic2,
        topic2Partition,
        topic2Batches
      )
    }

    val simFuture = Future.sequence(Seq(topic1Sim, topic2Sim))

    Await.ready(simFuture, Duration.Inf)
  }
}
