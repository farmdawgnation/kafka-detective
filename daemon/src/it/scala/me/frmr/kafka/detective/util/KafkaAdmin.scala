package me.frmr.kafka.detective.util

import com.typesafe.scalalogging.StrictLogging
import java.util.Properties
import org.apache.kafka.clients.admin._
import org.apache.kafka.clients.admin.AdminClientConfig._
import scala.collection.JavaConverters._

class KafkaAdmin(bootstrapServer: String) extends StrictLogging {
  /**
   * Create a topic on the Kafka cluster.
   */
  def createTopic(topicName: String, partitions: Int, replicationFactor: Int = 1): Unit = {
    logger.info(s"Creating topic $topicName")
    val adminProps = new Properties()
    adminProps.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
    val adminClient = AdminClient.create(adminProps)

    adminClient.createTopics(
      Seq(new NewTopic(topicName, partitions, replicationFactor.toShort)).asJava
    )

    adminClient.close()
  }
}
