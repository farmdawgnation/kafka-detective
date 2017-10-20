package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api.MonitorReporterTransformer

/**
 * A reporter transformer that invokes to string on the object provided for reporting.
 */
object ToStringTransformer extends MonitorReporterTransformer {
  def transform(input: Object): Object = input.toString
}
