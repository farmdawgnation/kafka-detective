package me.frmr.kafka.detective.api

/**
 * A wrapper around a message received from a Kafka topic. This is the Kafka Detective equivalent of
 * a standard `ConsumerRecord` from the Kafka client APIs. It is intentionally typed a bit
 * differently to facilitate use across JVM languages.
 *
 * @param messageOffset The offset of the message this envelope contains.
 * @param messagePartition The partition this message occured on.
 * @param messageTimestamp The timestamp associated with this message.
 * @param keyClass A `java.lang.Class` describing what the `keyInstance` actually is.
 * @param keyInstance The key for the message this envelope contains.
 * @param valueClass A `java.lang.Class` describing what the `valueInstance` actually is.
 * @param valueInstance The value of the message this envelope contains.
 */
case class MonitorObjectEnvelope(
  messageOffset: Long,
  messagePartition: Int,
  messageTimestamp: Long,
  keyClass: Class[_],
  keyInstance: Object,
  valueClass: Class[_],
  valueInstance: Object
)

/**
 * An interface that instructs Detective how to deserialize messages from byte arrays into concrete
 * Java objects. If you need to convert your objects into some custom structure for use in your
 * implementation of Detective, you can extend this class and override the `deserialize` method
 * to implement your deserialization algorithm.
 *
 * Instances of this class should have a zero-arg constructor.
 */
abstract class MonitorDeserializer {
  /**
   * Pack a `[[MonitorObjectEnvelope]]` with the provided values. Today, this method is a simple
   * pass through to the `[[MonitorObjectEnvelope]]` constructor, but in the future we may change
   * this implementation to do some sanity checking. It's recommended to use this method to pack
   * an envelope in custom deserializers instead of doing it directly for the best compatibility.
   *
   * @param offset The offset of the message to pack
   * @param partition The partition of the message to pack
   * @param timestamp The timestamp of the message to pack
   * @param deserializedKeyClass The `keyClass` of the message to pack.
   * @param deserializedKey The `keyInstance` of the message to pack.
   * @param deserializedValueClass The `valueClass` of the message to pack.
   * @param deserializedValue The `valueInstance` of the message to pack.
   */
  final def packEnvelope(
    offset: Long,
    partition: Int,
    timestamp: Long,
    deserializedKeyClass: Class[_],
    deserializedKey: Object,
    deserializedValueClass: Class[_],
    deserializedValue: Object
  ): MonitorObjectEnvelope = {
    MonitorObjectEnvelope(
      offset,
      partition,
      timestamp,
      deserializedKeyClass,
      deserializedKey,
      deserializedValueClass,
      deserializedValue
    )
  }

  /**
   * This method implements the actual deserialization behavior for a particular
   * `MonitorDeserializer` instance. If you're implementing your own custom deserializer this is
   * the message you should hook into.
   *
   * @param offset The message offset
   * @param partition The partition the message appeared on
   * @param timestamp The Kafka timestamp for the message
   * @param keyBytes The key byte array that the Kafka Consumer retrieved
   * @param valueBytes The value byte array that the Kafka Consumer retrieved
   * @return A [[MonitorObjectEnvelope]] containing the deserialized, packed message.
   */
  def deserialize(
    offset: Long,
    partition: Int,
    timestamp: Long,
    keyBytes: Array[Byte],
    valueBytes: Array[Byte]
  ): MonitorObjectEnvelope
}
