package me.frmr.kafka.detective

import me.frmr.kafka.detective.config.DetectiveConfig
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import java.io._

/**
 * Main class for the kafka detective monitoring.
 */
object Main extends StrictLogging with Metrics {
  metrics.gauge("jvm.heap.size") {
    Runtime.getRuntime().totalMemory()
  }

  metrics.gauge("jvm.heap.used") {
    Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
  }

  def main(args: Array[String]) = {
    logger.info("Starting kafka-detective...")
    logger.info("Picking up monitors from configuration...")

    val config = ConfigFactory.load()
    val detectiveConfig = DetectiveConfig(config)

    logger.info("Instantiating the runner...")
    val runner = new Runner(detectiveConfig)

    if (runner.monitorWardens.length == 0) {
      logger.error("No monitor wardens defined.")
    } else {
      logger.info("Starting metrics subsystem...")
      Metrics.start()

      logger.info("Starting monitor wardens...")
      runner.start()

      logger.info("Monitor wardens started.")

      // If we're killed by SIGTERM.
      Runtime.getRuntime().addShutdownHook(new Thread {
        override def run(): Unit = {
          logger.info("Shutting down monitor wardens...")
          runner.monitorWardens.foreach(_.stop())

          logger.info("Shutting down metrics subsystem...")
          Metrics.stop()
        }
      })
    }
  }
}
