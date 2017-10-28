name := "detective-api"

organization := "me.frmr.kafka"

scalaVersion := "2.12.4"

version := Version.version

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0"
libraryDependencies += "org.scalatest"              %% "scalatest"       % "3.0.1" % "test"

pomExtra :=
<url>https://github.com/farmdawgnation/kafka-detective</url>
<licenses>
  <license>
    <name>Apache 2</name>
    <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>
<scm>
  <url>https://github.com/farmdawgnation/kafka-detective.git</url>
  <connection>https://github.com/farmdawgnation/kafka-detective.git</connection>
</scm>
<developers>
  <developer>
    <id>farmdawgnation</id>
    <name>Matt Farmer</name>
    <email>matt@frmr.me</email>
  </developer>
</developers>
