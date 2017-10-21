name := "detective-daemon"

organization := "me.frmr.kafka"

scalaVersion := "2.12.2"

version := Version.version

val kafkaVersion = "0.10.2.0"

libraryDependencies ++= Seq(
  "org.apache.kafka"           %  "kafka-clients"   % kafkaVersion,
  "com.typesafe"               %  "config"          % "1.3.1",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0",
  "ch.qos.logback"             %  "logback-classic" % "1.2.2",
  "nl.grons"                   %% "metrics-scala"   % "3.5.6_a2.4",
  "io.dropwizard.metrics"      %  "metrics-graphite"% "3.2.0", //Keep this locked to the version in metrics-scala
  "net.liftweb"                %% "lift-json"       % "3.1.0-M3",
  "org.scalatest"              %% "scalatest"       % "3.0.1" % "test,it"
)

test in assembly := {}

mainClass in assembly := Some("me.frmr.kafka.detective.Main")

scalacOptions += "-deprecation"

Defaults.itSettings

parallelExecution in IntegrationTest := false

assemblyJarName in assembly := "kafka-detective.jar"
