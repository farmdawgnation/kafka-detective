**Kafka Detective** is a tool for continuous end-to-end testing of streaming data applications.

Internally at MailChimp, we use it for validating the correctness and performance of our Data
Pipeline.

## Overview

Detective is configured with **monitors** that monitor two topics. These topics may be on the same
Kafka cluster, or they may not be. The following terminology is used throughout the rest of this
document:

* The **reference topic** is the topic that is considered the "known good" implementation output.
* The **test topic** is the topic that is the output of our new implementation.

Information about these topics are described in a subject that tells Detective what server
to connect to, what topic to monitor, and what partitions to monitor. Detective currently
**requires** you to specify what partitions are monitored on each topic. We can't currently support
automatic group management, so you'll have to manually specify what partitions you want Detective
to monitor.

For each message that comes from the reference topic, Detective will deserialize the message
using a configured **deserializer** and add it to the **reference window**: the set of messages that
Detective will attempt to find messages in. The size of this reference window is configurable. Once
the reference window reaches its size, older messages are evicted from it.

For each message that comes from the test topic, Detective will deserialize it and try to find
a record from the reference window with the same key that _should_ match this record using the
provided **match finder**. Once a candidate message is identified, the value of the message from the
test topic and the value of the message from the reference topic are compared using the configured
**match tester**. The results of these find and test efforts are recorded to the **reporters**
configured, which can report to anything: a metrics system, another kafka topic, etc.

The kinds of results reported are:

* **Successful match:** A matching record was found in the reference topic for this test message.
* **Failed match:** A record that should have matched the test message existed in the reference
  topic (e.g. the key matched), but the value of the message didn't match.
* **No match:** No record with a matching key was found in the reference topic.
* **Ignored message:** A record from the test topic was ignored. This could happen if Detective
  realizes that the reference window cannot contain the test message (based on the offsets of the
  messages currently visible) or if the match finder instructed Detective to ignore the message.

Users of Kafka Detective can provide their own deserializers, match finders, match testers, and
reporters to suit their needs. These extensions can be developed by pulling in the
kafka-detective-api package into a Java or Scala project, building a JAR, and then putting that JAR
on the Detective Daemon class path. After that, the custom extensions you developed can be used
by simply configuring them for your monitors.

## Configuration Summary

### Root configuration

|Setting name                   |Type                  |Req? |Description                                          |
|-------------------------------|----------------------|-----|-----------------------------------------------------|
|max-worker-count-per-monitor   |Int                   |Y    |Maximum number of workers for processing messages    |
|comparison-threads-per-worker  |Int                   |Y    |Number of comparison threads each worker should use  |
|monitors                       |Array[MonitorConfig]  |Y    |Configuration of monitors                            |

### Monitor configuration

|Setting name            |Type          |Req? |Description                                                          |
|------------------------|--------------|-----|---------------------------------------------------------------------|
|identifier              |String        |Y    |Unique identifier for the monitor. Used to calculate group id        |
|reference-subject       |SubjectConfig |Y    |Description of the reference topic                                   |
|test-subject            |SubjectConfig |Y    |Description of the test topic                                        |
|reference-window-size   |Int           |Y    |The number of reference windows to buffer and search for matches in  |
|match-finder            |String        |Y    |Name of a class conforming to `MonitorMatchFinder`                   |
|match-tester            |String        |Y    |Name of a class conforming to `MonitorMatchTester`                   |
|reporters               |String        |Y    |Comma-delimited names of classes conforming to `MonitorReporter`     |
|reporter-configs        |Object        |Y    |Configuration passed into all reporters. See notes below.            |

### Subject configuration

|Setting name  |Type       |Req?|Description                                                                        |
|--------------|-----------|----|-----------------------------------------------------------------------------------|
|server        |String     |Y   |The server to connect to for monitoring this topic.                                |
|topic         |String     |Y   |The topic to monitor.                                                              |
|partitions    |String     |Y   |Comma-delimited list of partitions to monitor.                                     |
|deserializer  |String     |Y   |Name of a class conforming to `MonitorDeserializer`                                |
