package me.frmr.kafka.detective.util

import com.typesafe.scalalogging.StrictLogging
import sys.process._

/**
 * Kafka administrative tools that call out to the Kafka shell scripts.
 *
 * This class expects to find the path to kafka-topics.sh in the environment
 * variable KAFKA_TOPICS_PATH. If not it'll log that it defaulted to just
 * "kafka-topics.sh" which will work - so long as its on your path.
 *
 * @param zkAddress IP address to ZooKeeper.
 */
class KafkaAdmin(zkAddress: String) extends StrictLogging {
  private[this] val kafkaTopicPath = {
    sys.env.get("KAFKA_TOPICS_PATH") match {
      case Some(path) =>
        path

      case None =>
        logger.info(
          "Environment variable KAFKA_TOPICS_PATH should be set to the path for a kafka-topics " +
            "binary of version 0.10.0.1 or earlier. Because it has not been provided a default of `kafka-topics.sh` " +
            "has been inferred"
        )

        "kafka-topics.sh"
    }
  }

  /**
   * Create a topic on the Kafka cluster.
   */
  def createTopic(topicName: String, partitions: Int): Unit = {
    val cmd = Seq(
      kafkaTopicPath,
      s"--zookeeper=$zkAddress",
      "--create",
      s"--topic=$topicName",
      s"--partitions=$partitions",
      "--replication-factor=1"
    )

    val result = cmd.!

    if (result != 0) {
      throw new RuntimeException(s"Exit with code $result when running $cmd")
    }
  }
}
