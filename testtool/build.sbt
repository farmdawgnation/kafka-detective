name := "detective-testtool"

organization := "me.frmr.kafka.testtool"

version := "0.1.0"

scalaVersion := "2.12.2"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

libraryDependencies += "org.apache.kafka" %  "kafka-clients" % KafkaVersion.version

libraryDependencies += "net.liftweb" %% "lift-json" % "3.1.0-M3"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.13.4"
