package me.frmr.kafka.detective.deserializer

import me.frmr.kafka.detective.api._

/**
 * A simple deserializer that processes incoming bytes as UTF-8 strings.
 */
class StringDeserializer extends MonitorDeserializer {
  override def deserialize(
    offset: Long,
    partition: Int,
    timestamp: Long,
    keyBytes: Array[Byte],
    valueBytes: Array[Byte]
  ): MonitorObjectEnvelope = {
    val deserializedKeyPojo = new String(keyBytes, "UTF-8")
    val deserializedKeyClass = classOf[String]

    val deserializedValuePojo = new String(valueBytes, "UTF-8")
    val deserializedValueClass = classOf[String]

    packEnvelope(offset, partition, timestamp, deserializedKeyClass, deserializedKeyPojo, deserializedValueClass, deserializedValuePojo)
  }
}
