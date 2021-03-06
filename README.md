# Kafka Detective

[![Build Status](https://travis-ci.org/farmdawgnation/kafka-detective.svg?branch=master)](https://travis-ci.org/farmdawgnation/kafka-detective)
[![Maven Central](https://img.shields.io/maven-central/v/me.frmr.kafka/detective-api_2.12.svg)]()
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/872abfe7511b49998707a4aabb2b4691)](https://www.codacy.com/app/farmdawgnation/kafka-detective?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=farmdawgnation/kafka-detective&amp;utm_campaign=Badge_Grade)

Kafka Detective is a tool for continuous end-to-end testing of streaming data applications.

Internally at MailChimp, we use it for validating the correctness and performance of our Data
Pipeline. This project is stable and under active development, but documentation is an ongoing
effort.

## Documentation

For learning about how Detective works, we recommend reading our documentation
site: https://detective.frmr.me.

## Getting in Touch and Contributing

Questions, bugs, feature requests and more should all be filed as issues. Discussion about the
project will take place in the issues system.

Contributions are welcome. Please see the [contribution guide](https://github.com/farmdawgnation/kafka-detective/blob/master/CONTRIBUTING.md)
for more information.

## Building

To build this project you'll need to use:

* JDK 8
* [sbt](http://www.scala-sbt.org/)

Once both are installed and set up, you should be able to spin up `sbt` to get things up and
running. _Do be advised sbt doesn't work with JDK 9._

## About

This software was developed at [MailChimp](https://mailchimp.com/), who generously allowed me to
open source it under the Apache 2 License. I also help maintain several other open source projects,
including [the Lift Framework](https://liftweb.net) and [Dispatch](https://dispatchhttp.org/Dispatch.html).
