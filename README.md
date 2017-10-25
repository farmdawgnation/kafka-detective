**Kafka Detective** is a tool for continuous end-to-end testing of streaming data applications.

Internally at MailChimp, we use it for validating the correctness and performance of our Data
Pipeline.

**Note:** The docker image is available from Docker Hub, but we haven't gotten the api package
on Sonatype yet. We're working on it, but if you want to write your own extensions for now
you'll need to clone the project and publish the API to your local ivy cache.

## Documentation

For learning about how Detective works, we recommend reading our (work-in-progress) documentation
site at https://detective.frmr.me.

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
