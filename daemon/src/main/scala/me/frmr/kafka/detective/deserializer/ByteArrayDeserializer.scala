package me.frmr.kafka.detective.deserializer

import me.frmr.kafka.detective.api._

/**
 * A simple no-op deserializer that simply returns the bytes that came from Kafka. If your testing
 * code is doing simple equivalance checks this will be fastest deserializer for you to use. If,
 * however, you're looking at doing more nuanced checks (ignoring certain fields, etc) then you
 * will want to provide something more specialized than this implementation.
 */
class ByteArrayDeserializer extends MonitorDeserializer {
  override def deserialize(
    offset: Long,
    partition: Int,
    timestamp: Long,
    keyBytes: Array[Byte],
    valueBytes: Array[Byte]
  ): MonitorObjectEnvelope = {
    val deserializedKeyPojo = keyBytes
    val deserializedKeyClass = classOf[Array[Byte]]

    val deserializedValuePojo = keyBytes
    val deserializedValueClass = classOf[Array[Byte]]

    packEnvelope(offset, partition, timestamp, deserializedKeyClass, deserializedKeyPojo, deserializedValueClass, deserializedValuePojo)
  }
}
