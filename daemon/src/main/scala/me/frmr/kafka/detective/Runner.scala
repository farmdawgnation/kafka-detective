package me.frmr.kafka.detective

import me.frmr.kafka.detective.api._
import me.frmr.kafka.detective.config._
import me.frmr.kafka.detective.domain._
import me.frmr.kafka.detective.monitor._
import java.util.{Map => JMap}

/**
 * The runner class is responsible for interpreting config and turning into
 * concrete objects. It exposes simple start/stop methods to start and stop
 * whatever objects it creates.
 *
 * @param config The DetectiveConfig object for this run
 */
class Runner(config: DetectiveConfig) {
  val monitorWardens: Seq[MonitorWarden] = config.monitors.map { monitorConfig =>
    def getDeserializer(className: String)(): MonitorDeserializer = {
      Class.forName(className)
        .newInstance()
        .asInstanceOf[MonitorDeserializer]
    }

    val referenceSubject = MonitorSubject(
      monitorConfig.referenceSubjectConfig.server,
      monitorConfig.referenceSubjectConfig.topic,
      monitorConfig.referenceSubjectConfig.partitions,
      getDeserializer(monitorConfig.referenceSubjectConfig.deserializerClassName) _
    )

    val testSubject = MonitorSubject(
      monitorConfig.testSubjectConfig.server,
      monitorConfig.testSubjectConfig.topic,
      monitorConfig.testSubjectConfig.partitions,
      getDeserializer(monitorConfig.testSubjectConfig.deserializerClassName) _
    )

    def matchFinder(): MonitorMatchFinder = {
      Class.forName(monitorConfig.matchFinderClassName)
        .newInstance()
        .asInstanceOf[MonitorMatchFinder]
    }

    def matchTester(): MonitorMatchTester = {
      Class.forName(monitorConfig.matchTesterClassName)
        .newInstance()
        .asInstanceOf[MonitorMatchTester]
    }

    def reporters(): Seq[MonitorReporter] = {
      monitorConfig.reporterClassNames.map { reporterClassName =>
        Class.forName(reporterClassName)
          .getConstructor(classOf[String], classOf[JMap[_, _]])
          .newInstance(monitorConfig.identifier, monitorConfig.reporterConfigs)
          .asInstanceOf[MonitorReporter]
      }
    }

    val monitor = Monitor(
      monitorConfig.identifier,
      referenceSubject,
      testSubject,
      matchFinder _,
      matchTester _,
      reporters _,
      monitorConfig.referenceWindowSize,
    )

    new MonitorWarden(
      monitor,
      config.maxWorkerCountPerMonitor,
      config.comparisonThreadsPerWorker,
      config.emptyQueuePollIntervalMs,
      config.queueResetMs
    )
  }

  def start() = {
    monitorWardens.foreach(_.start())
  }

  def stop() = {
    monitorWardens.foreach(_.stop())
  }
}
