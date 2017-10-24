
# Kafka Detective

> This documentation site is still being refined and organized. Please file issues about anything
  that is unclear. We also welcome pull requests from anyone who can help us better organize and
  clarify this information.

**Kafka Detective** is a utility for performing continuous end-to-end testing of streaming data
applications.

This site describes how to use Kafka Detective to debug your streaming data applications, how to
extend Kafka Detective with extensions, and walks through some of the architecture of
Detective.

We'll try our darndest to keep this document up to date as changes come to Detective, but we
encourage you to open a PR fixing any issues you find along the way.

## Latest Release

The latest release of Kafka Detctive is **[0.7.1](https://github.com/farmdawgnation/kafka-detective/releases/tag/0.7.1)**.

## Using Kafka Detective

This section walks through deploying and using Kafka Detective in your environment. Each of the
subsections walk through a different aspect of configuring and deploying Kafka Detective.

## Environment Pre-Requisites

Detective works by attaching to two topics. A **test topic** representing the code you'd like to
eventually deploy to production (for many this is a staging environment) and a **reference
environment** that is your known good (for many this is their production environment). Detective
makes a few assumptions about the state of the environment it's going to be attached to.
Specifically those assumptions are:

* The respective environments _should_ be producing the exact same data.
  * The two topics you're monitoring need to have the same number of partitions.
* The environments are in a healthy state when Kafka Detective is first connected. Specifically:
  * The environments are performing at the same speed.
  * The data coming out of the environments matches.
  * The environments are processing the same inputs.

Additionally, to work best Kafka Detective needs a few things to be present in your infrastructure:

* A Graphite Carbon host to feed metrics into.
* Ideally, a Grafana instance (or something similar) dashboarding from those metrics.
* An ElasticSearch instance for Detective to report errors to.
* Ideally, a Kibana instance (or something similar) to analyze failures reported to ES.

If all of the above conditions are true, Kafka Detective should be able to start producing
meaningful output in your environment pretty quickly.

## Configuring Detective

Kafka Detective has two methods of configuration for most application settings. You can either
use an application.conf file, which will work best if you're running Detective directly on the
host, or you can configure using environment variables, which will work best for Docker deployment.

Unfortunately, for metrics configuration options you currently _must_ use environment variables.

### General Application Configuration

General application configuration covers the basic configuration of what content Detective is
monitoring and how it is monitoring it.

#### Configuring the Docker deployment

When deploying as a Docker container, we recommend using the environment to configure the
application. The following environment variables are available for you to use to configure
Detective:

* `MAX_WORKER_COUNT` - The maximum number of workers to use. When detective boots up each partition
  on your upstream topics will be assigned to workers. The correct value here is determined by the
  volume on your topics. For high volume topics, this should be equal to the number of partitions
  you're monitoring.
* `COMPARISON_THREADS_PER_WORKER` - The number of different comparison threads to use while testing
  messages.
* `MONITOR_ID` - Some unique identifier for the monitor.
* `REFERENCE_SERVER` - The Kafka broker address (or a comma separated list of broker addresses) that
  the reference data will come from.
* `REFERENCE_TOPIC` - The topic that the reference data will come from.
* `REFERENCE_PARTITION` - A comma separated list of partitions to monitor from the topic.
* `REFERENCE_DESERIALIZER` - The deserializer to use when parsing reference messages.
* `TEST_SERVER` - The Kafka broker address (or a comma separated list of broker addresses) that
  the test data will come from.
* `TEST_TOPIC` - The topic that the test data will come from.
* `TEST_PARTITION` - A comma separated list of partitions to monitor from the test topic.
* `TEST_DESERIALIZER` - The deserializer to use when parsing reference messages.
* `REFERENCE_WINDOW_SIZE` - The number of reference messages to buffer for comparison.
* `MATCH_FINDER` - The class that will serve to find candidate matches.
* `MATCH_TESTER` - The class that will test match candidates for matching.
* `REPORTERS` - The comma separated list of reporter to use.

If you're using one of the Kafka topic reporters, you'll also want to configure these environment
variables:

* `KAFKA_REPORTER_TOPIC` - The topic to report on
* `KAFKA_REPORTER_SERVER` - The broker address (or comma separated list of broker addresses) that
  failures will be reported to.

#### Writing an application.conf

When deploying Detective as a JAR, you'll need to write an `application.conf` file and put it on
your server. Detective uses [Typesafe Config][tsconfig] for its configuration. You can find more
information on the different kinds of things that could appear in a config file in their
documentation.

Below is an example configuration file including information about what each value means:

```
kafka-detective {
  # The maximum number of workers to allocate per
  # monitor
  max-worker-count-per-monitor = 10

  # The number of threads to use for comparing
  # messages in a worker
  comparison-threads-per-worker = 10

  # An array of monitors. You can add multiple
  # monitors to a single instance of Detective
  monitors = [
    {
      # An identifier for this monitor.
      # All monitor identifiers must be
      # unique.
      identifier = "awesome-topic"

      # Information about the reference
      # topic.
      reference-subject = {
        # Hostname and port of the broker
        # to connect to
        server = "localhost:9092"

        # Topic to monitor
        topic = "awesome-ref-topic"

        # Partitions in the topic to monitor
        partitions = "1,2,3"

        # Class that can deserialize the
        # messages
        deserializer = "me.frmr.kafka.detective.deserializer.ByteArrayDeserializer"
      }

      # Same configuration as above for
      # the test topic
      test-subject = {
        server = "localhost:9092"
        topic = "awesome-test-topic"
        partitions = "1,2,3"
        deserializer = "me.frmr.kafka.detective.deserializer.ByteArrayDeserializer"
      }

      # The number of messages from the reference
      # topic to buffer for comparison
      reference-window-size = 10000

      # Class that can will find potential matches
      # within the reference window.
      match-finder = "me.frmr.kafka.detective.matchfinder.KeyEqualsFinder"

      # Class that will determine if a match is
      # successful.
      match-tester = "me.frmr.kafka.detective.matchtester.ValueEqualsTester"

      # Class that will report failures.
      reporters = "me.frmr.kafka.detective.reporter.KafkaTopicReporter"

      # Special configuration for reporters
      # that require them.
      reporter-configs {
        kafka {
          topic = "awesome-topic-fails"

          producer {
            "bootstrap.servers" = "localhost:9092"
            "acks" = "all"
          }
        }
      }
    }
  ]
}
```

[tsconfig]: https://github.com/typesafehub/config

### Metrics Reporting Configuration

Detective uses Dropwizard Metrics under the hood to report on a number of interesting metrics about
its operation. Additionally, there's a metrics reporter that you can use to report metrics on how
your performing overall.

By default, these metrics will all be fed to JMX. However, you can configure the metrics subsystem
to report to a [Graphite Carbon](https://github.com/graphite-project/carbon) host by providing the
following environment variables:

https://github.com/graphite-project/carbon

* `GRAPHITE_ENABLED=1`
* `GRAPHITE_HOST=my.carbon.host`
* `GRAPHITE_PORT=1234`
* `GRAPHITE_POLL_SECONDS=` any value you wish for refreshing

## Default Implementations

Kafka Detective provides a few helpful, default implementations that can be used without
doing any custom coding of your own. Each section tells you the environment variable or config
parameter that lets you use these default implemenations.

### MatchFinder: KeyEqualsFinder

> MATCH_FINDER="me.frmr.kafka.detective.matchfinder.KeyEqualsFinder"

Determines possible matches based on the equality of the key. Attempts to account for duplicate
keys by keeping track of the offset of the last probable match for a provided key. This class is
provided as a part of detective-api so that you can compose on top of it for custom functionality.

### MatchTester: ValueEqualsTester

> MATCH_TESTER="me.frmr.kafka.detective.matchtester.ValueEqualsTester"

Does a simple comparison and determines if the value of the two messages are equal. Checks for
byte arrays and uses an appropriate array equality checker if found. If not, falls back to
the `equals` method.

### Deserializer: ByteArrayDeserializer

> REFERENCE_DESERIALIZER="me.frmr.kafka.detective.ByteArrayDeserializer"

> TEST_DESERIALIZER="me.frmr.kafka.detective.ByteArrayDeserializer"

Deserializes key and value as byte arrays.

### Deserializer: StringDeserializer

> REFERENCE_DESERIALIZER="me.frmr.kafka.detective.StringDeserializer"

> TEST_DESERIALIZER="me.frmr.kafka.detective.StringDeserializer"

Deserializes key and value as strings.

### Deserializer: StringKeyByteArrayValueDeserializer

> REFERENCE_DESERIALIZER="me.frmr.kafka.detective.StringKeyByteArrayValueDeserializer"

> TEST_DESERIALIZER="me.frmr.kafka.detective.StringKeyByteArrayValueDeserializer"

Deserializes key as string and value as byte array.

## Deploying Detective

The Detective Daemon can be deployed as a stand-alone JAR or as a Docker image.

### Stand-alone JAR Deployment

When deploying Detective as a stand-alone JAR you'll need to roughly do the following:

1. Upload the JAR to some known location on the server you'd like to deploy to.
2. Populate a configuration file on the server. (See below.)
3. Pick a location to upload extensions to if you have any.
4. Create an init script or monit script to start the server. (See below.)

Once you've done the above you should be in pretty good shape to start running Detective.

#### Preparing your server

To prepare your server, you should pick a few directories for deploying the pieces of Detective.
Those locations (with our default suggestions) are:

* The location where the Detective JAR is stored. We'll call this `DETECTIVE_HOME` in this doc.
  (we recommend `/opt/kafka-detective`)
* The location where Detective extensions are stored. We'll call this `LIBS_HOME` in this doc.
  (we recoomend `opt/kafka-detective/lib`)

#### Generating the start command

Once Kafka Detective is on your server, you'll need to correctly craft the command to start it.
A start script should look something like this with your `DETECTIVE_HOME` and `LIBS_HOME` substituted
in:

```
#!/usr/bin/env bash

exec java -cp DETECTIVE_HOME/*:LIBS_HOME/* me.frmr.kafka.detective.Main "$@"
```

### Docker image deployment

Detective also supports deployment using a Docker image. The currently released docker image is:

```
farmdawgnation/kafka-detective:0.7.1
```

## Extending Kafka Detective

Kafka Detective provides interfaces that allow you to extend its behavior. Those interfaces
are available in the `detective-api` package in Maven Central.

### Getting detective-api

To get started, you'll need to pull in `detective-api` as a provided dependency for your project.
You can write extensions in Scala 2.12 or in Java. Below are code snippets for adding `detective-api`
to various build systems.

> As of the time of this writing, detective-api isn't yet available on Maven Central. I'm working
  on it. Brought the wrong computer for Maven publishing with me to All Things Open. In the
  meantime, you can clone the repo, install sbt, and run "sbt api/publishLocal" to make it
  available in your local ivy cache.

#### SBT

```
libraryDependencies += "me.frmr.kafka" %% "detective-api" %% "0.7.1" % "provided"
```

#### Gradle

```
dependencies {
  compileOnly 'me.frmr.kafka:detective-api:0.7.1'
}
```

#### Maven

```
<dependency>
  <groupId>me.frmr.kafka</groupId>
  <artifactId>detective-api</artifactId>
  <version>0.7.1</version>
  <scope>provided</scope>
</dependency>
```

### Using detective-api

Once you've got your project defined, you can defined implementations for the interfaces provided
by the `me.frmr.kafka.detective.api` package.

Scaladocs for the API can be found [here](/scaladocs/detective-api).

Specifically, those interfaces are:

* **MonitorDeserializer** — Provides a custom deserializer for incoming messages. A different
  deserializer can be defined for the test and reference side to accommodate for changes in
  serialization format.
* **MonitorMatchFinder** - Provides a custom heuristic for determining if a message should match
  any possible messages from a reference window.
* **MonitorMatchTester** - Provides a custom heuristic for testing the pair produced by the
  configured MonitorMatchFinder.
* **MonitorReporter** - Reports matching results to an external system.
* **MonitorReporterTransformer** - Transforms messages before sending them to the reporting
  system. This could be used, for example, to transform the messages to JSON format.

In your project you will create some custom implementation of one or more of the above interfaces.
Then, when you're ready you'll need to use your build tool's packaging task to create a JAR of
the compiled classes.

Once you've done this, you'll want to deploy this JAR into Detective's classpath. We outline how
to do this with the Docker image below. You will need to adjust this technique if you've deployed
Detective differently.

### Extend via a volume mount

The easiest possibility for deploying your custom extensions to Detective is to simply volume
mount them into the Kafka Detective container. Simply execute the following at your shell:

```
docker run -v /path/to/file.jar:/opt/kafka-detective/lib/file.jar farmdawgnation/kafka-detective:0.7.1
```

Detective scans all JARs found in the lib folder for classes, so simply making it available to
the default Docker image in this way is sufficient to get your custom filters and testers available
to Detective.

### Extend via a new Docker Image

One way to deploy your code is by making a variant of the Detective docker image that
adds your JAR file to the `lib` folder, where Detective looks for classes. That Docker image
can be defined with a Dockerfile as simple as:

```
FROM farmdawgnation/kafka-detective:0.7.1

ADD /path/to/your/jar/file.jar /opt/kafka-detective/lib/file.jar
```

You'll then build and tag that image and push it somewhere your server can access it. Then,
all you need to do is change the appropriate config setting to use your class instead of the
default. For example, if you had provided a custom match finder, you might change the
`MATCH_FINDER` environment variable before running the image.

## Architecture Overview

Detective deployments define **monitors** that represent an individual test of two topics when
compared to each other. Monitors work by keeping an eye on a **reference topic** that represents
the known-or-believed-to-be-good production implementation and a **test topic** that represents
some newer implementation that you would like to test against. Each monitor explicitly enumerates
what partitions it monitors.

Each monitor has some number of **monitor workers**. These workers are responsible for some number
of partitions from the reference and test topics. The workers will consume messages and perform
parallelized comparison of messages. Messages ready for comparison by the worker are routed to
threads in the thread pool by their Kafka key to ensure that no two messages with the same key
are compared concurrently. This permits us to ensure ordering is correct in the test topic.

As Kafka messages come in on both topics, they are run through a **deserializer** that turns the
byte arrays provided by Kafka into concrete objects that the later stages of Detective can use.
These are wrapped in an envelope for the convenience of the later parts of the process.

As messages come into Detective from the reference topic, they are placed into the **reference
window**: a configurable fixed-size window for the worker to consider when attempting to find
matching messages from the test topic.

As messages come into Detective from the test topic, they are submitted to the comparison thread
pool for comparison to reference messages. The process for comparison is:

1. Determine, based on message offsets, if we believe this message is reasonably present in the
   reference window. If not, wait until the reference topic has consumed enough messages that we
   think this message should exist in the reference window.
2. Execute the configured **match finder** to determine if there are any messages in the reference
   window that we think _should_ totally match the test message we're considering.
   * If we do find such a message, we return it and submit it for further testing.
   * If we do not find such a message, we consider this test message a **key miss** and move on to
     the next message. We report key misses to the configured **reporters** for the monitor.
   * Additionally, the finder could report that this is a message that _should not_ be tested. If so
     this message will be discarded and reported as an **ignored message**, and testing will move on
     to the next message. We report ignored messages to the **reporters** for the monitor.
3. Execute the configured **match tester** against the test message and the reference message we
   believe should match.
   * If this test passes, we consider this test message a **successful match** and move on to the
     next message. We report successful matches to the configured **reporters** for the monitor.
   * If this test fails, we consider this test message a **failed match** and move on to the next
     message. We report failed matches to the configured **reporters** for the monitor.

While doing this process, the monitor worker also ensures that both topics are roughly in sync and
applies backpressure to the underlying consumers as needed to keep them in sync. In the event that
backpressure has to be applied consistently for some span of time (5 minutes by default), the
worker will invoke a **worker reset** and seek to the end of both topics. Any messages queues for
testing when this happens will be reported as **ignored messages**.

### Reporting

As mentioned above, Detective reports all test results to the configured reporters. Each reporter
gets to decide how it wants to handle those reports. Below is an overview of how each reporter
packaged with Detective handles those reports.

#### Logging reporter

Successful matches and failed matches are reported at the `INFO` log level. Key misses are
reported at the `TRACE` log level. Ignored messages are reported at the `DEBUG` log level.

#### Metrics reporter

The metrics reporter uses the built in metrics configuration for Detective writ large, and
requires that the environment variables for reporting to metrics are properly configured. This
reporter will report all kinds of test results as marks on meters.

Replacing `MONITOR_IDENTIFIER` with the actual monitor identifier, the paths for these appear in
JMX as:

* `reporter.MONITOR_IDENTIFIER.successful-match`
* `reporter.MONITOR_IDENTIFIER.failed-match`
* `reporter.MONITOR_IDENTIFIER.no-match`
* `reporter.MONITOR_IDENTIFIER.ignored`

#### Kafka topic reporter

This reporter will report results to a Kafka topic. To do so, it will serialize the error message
and relevant messages as JSON and publish it to some configured Kafka topic.

> Please note this reporter requires additional configuration to work correctly. See the notes
> on the configuration page.

This reporter will **only** report on failed matches or key misses.
