package me.frmr.kafka.detective.config

import com.typesafe.config.{Config => TypesafeConfig, ConfigObject => TypesafeConfigObject}
import scala.collection.JavaConverters._
import java.util.{Map => JMap}

case class SubjectConfig(
  server: String,
  topic: String,
  partitions: Seq[Int],
  deserializerClassName: String
)
object SubjectConfig {
  def apply(config: TypesafeConfig): SubjectConfig = {
    val server = Option(config.getString("server")).getOrElse {
      throw new IllegalStateException("Missing server declaration in subject config")
    }
    val topic = Option(config.getString("topic")).getOrElse {
      throw new IllegalStateException("Missing topic declaration in subject config")
    }

    val partitions = if (config.hasPath("partitions")) {
      Option(config.getString("partitions").split(",").map(_.toInt).toSeq).getOrElse(Nil)
    } else {
      Nil
    }

    val deserializerClassName = Option(config.getString("deserializer")).getOrElse {
      throw new IllegalStateException("deserializerClassName for monitor subject was not provided")
    }

    SubjectConfig(
      server,
      topic,
      partitions,
      deserializerClassName
    )
  }
}

case class MonitorConfig(
  identifier: String,
  referenceSubjectConfig: SubjectConfig,
  testSubjectConfig: SubjectConfig,
  matchFinderClassName: String,
  matchTesterClassName: String,
  reporterClassNames: Seq[String],
  reporterConfigs: JMap[String, Object],
  referenceWindowSize: Int
)
object MonitorConfig {
  def apply(config: TypesafeConfig): MonitorConfig = {
    val identifier = Option(config.getString("identifier")).getOrElse {
      throw new IllegalStateException("identifier for monitor was not provided")
    }

    val matchFinderClassName = Option(config.getString("match-finder")).getOrElse {
      throw new IllegalStateException("matchFinderClassName for monitor was not provided")
    }

    val matchTesterClassName = Option(config.getString("match-tester")).getOrElse {
      throw new IllegalStateException("matchTesterClassName for monitor was not provided")
    }

    val reporterClassNames = Option(config.getString("reporters").split(",").toSeq).getOrElse {
      throw new IllegalStateException("reporterClassName for monitor was not provided")
    }

    val reporterConfigs = Option(config.getObject("reporter-configs").unwrapped()).getOrElse {
      throw new IllegalStateException("reporter-configs for monitor was not provided")
    }

    val referenceWindowSize = Option(config.getInt("reference-window-size")).getOrElse {
      throw new IllegalStateException("referenceWindowSize for monitor was not provided")
    }

    MonitorConfig(
      identifier,
      SubjectConfig(config.getConfig("reference-subject")),
      SubjectConfig(config.getConfig("test-subject")),
      matchFinderClassName,
      matchTesterClassName,
      reporterClassNames,
      reporterConfigs,
      referenceWindowSize
    )
  }
}

case class DetectiveConfig(
  monitors: Seq[MonitorConfig],
  maxWorkerCountPerMonitor: Int,
  comparisonThreadsPerWorker: Int,
  emptyQueuePollIntervalMs: Int,
  queueResetMs: Int
)
object DetectiveConfig {
  def apply(config: TypesafeConfig): DetectiveConfig = {
    val unparsedMonitorsConfig = config.getList("kafka-detective.monitors").iterator.asScala

    val maxWorkerCountPerMonitor = Option(config.getInt("kafka-detective.max-worker-count-per-monitor")).getOrElse {
      throw new IllegalStateException("kafka-detective.max-worker-count-per-monitor must be configured")
    }

    val comparisonThreadsPerWorker = Option(config.getInt("kafka-detective.comparison-threads-per-worker")).getOrElse {
      throw new IllegalStateException("kafka-detective.comparison-threads-per-worker must be configured")
    }

    val emptyQueuePollIntervalMs = Option(config.getInt("kafka-detective.queue-poll-interval-ms")).getOrElse {
      throw new IllegalStateException("kafka-detective.queue-poll-interval-ms must be configured")
    }

    val queueResetMs = Option(config.getInt("kafka-detective.queue-reset-ms")).getOrElse {
      throw new IllegalStateException("kafka-detective.queue-reset-ms must be configured")
    }

    val monitors = unparsedMonitorsConfig.collect {
      case monitorObject: TypesafeConfigObject =>
        val monitorObjectConfig = monitorObject.toConfig()

        MonitorConfig(monitorObjectConfig)
    }

    DetectiveConfig(
      monitors.toSeq,
      maxWorkerCountPerMonitor,
      comparisonThreadsPerWorker,
      emptyQueuePollIntervalMs,
      queueResetMs
    )
  }
}
