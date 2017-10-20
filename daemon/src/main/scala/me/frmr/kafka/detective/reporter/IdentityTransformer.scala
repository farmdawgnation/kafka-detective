package me.frmr.kafka.detective.reporter

import me.frmr.kafka.detective.api.MonitorReporterTransformer

/**
 * A reporter transformer that does nothing to the object provided for reporting
 */
object IdentityTransformer extends MonitorReporterTransformer {
  def transform(input: Object): Object = input
}
