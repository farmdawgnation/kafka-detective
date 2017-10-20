package me.frmr.kafka.detective

import com.codahale.metrics._
import com.codahale.metrics.graphite._
import com.typesafe.scalalogging.StrictLogging
import java.util.concurrent._
import java.net._
import nl.grons.metrics.scala._

/**
 * Configures metrics reporting for kafka-detective. Supports reporting to Graphite or JMX.
 *
 * To enable Graphite reporting, provide the following envrionment variables:
 * * GRAPHITE_ENABLED=1
 * * GRAPHITE_HOST=graphite.yourhost.com
 * * GRAPHITE_PORT=1234
 * * GRAPHITE_POLL_SECONDS=10
 *
 * Adjust the values to meet your needs. If Graphite isn't configured, JMX will be used by default.
 */
object Metrics extends StrictLogging {
  val metricRegistry = new MetricRegistry()
  val reporter = Option(System.getenv("GRAPHITE_ENABLED")) match {
    case Some("1") =>
      val graphite = new Graphite(
        new InetSocketAddress(System.getenv("GRAPHITE_HOST"), System.getenv("GRAPHITE_PORT").toInt)
      )

      val metricPrefix = Option(System.getenv("STATSD_NAMESPACE")) match {
        case Some(prefix) =>
          "direct." + System.getenv("STATSD_NAMESPACE")

        case None =>
          "testing.kafka-detective"
      }

      GraphiteReporter
        .forRegistry(metricRegistry)
        .prefixedWith(metricPrefix)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite)

    case _ =>
      JmxReporter
        .forRegistry(metricRegistry)
        .inDomain("kafka-detective")
        .build()
  }


  def start() {
    reporter match {
      case graphite: GraphiteReporter =>
        logger.info("Graphite reporting activated.")
        graphite.start(System.getenv("GRAPHITE_POLL_SECONDS").toLong, TimeUnit.SECONDS)

      case jmx: JmxReporter =>
        logger.info("JMX reporting activated.")
        jmx.start()

      case _ =>
        throw new RuntimeException("Unexpected metrics reporter found while starting")
    }
  }

  def stop() {
    reporter match {
      case graphite: GraphiteReporter =>
        graphite.stop()

      case jmx: JmxReporter =>
        jmx.stop()

      case _ =>
        throw new RuntimeException("Unexpected metrics reporter found while stopping")
    }
    metricRegistry.removeMatching(MetricFilter.ALL)
  }
}

trait Metrics extends InstrumentedBuilder {
  override lazy val metricBaseName = MetricName("kafka-detective")
  val metricRegistry = Metrics.metricRegistry
}
