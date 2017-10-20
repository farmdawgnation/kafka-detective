package me.frmr.kafka.detective.api

/**
 * In the Kafka Detective world, a `MonitorReporterTransformer` is responsible for doing any kind
 * of transformation required before writing the object out to some external source. Not all
 * reporters may make use of a transformer, and they each have their own config keys for configuring
 * the transformers.
 *
 * Instances of this class should have a zero-arg constructor.
 */
trait MonitorReporterTransformer {
  def transform(thing: Object): Object
}
