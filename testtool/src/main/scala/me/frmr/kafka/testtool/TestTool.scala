package me.frmr.kafka.testtool

case class TestToolConfiguration(
  numberOfMessages: Int = 0,
  numberOfSeconds: Int = 0,

  bootstrapServer: String = "",

  topic1Topic: String = "",
  topic1Partition: Option[Int] = None,

  topic2Topic: String = "",
  topic2Partition: Option[Int] = None,

  repeatedKeyProbability: Double = 0.0,

  lagProbability: Double = 0.0,
  minimumLagMilliseconds: Int = 0,
  maximumLagMilliseconds: Int = 0,
  enableTopic1Lag: Boolean = true,
  enableTopic2Lag: Boolean = true,

  unevenDistributionProbability: Double = 0.0,
  maximumDistributionVariance: Double = 0.0,
  enableTopic1Uneven: Boolean = true,
  enableTopic2Uneven: Boolean = true
)

object TestTool {
  val parser = new scopt.OptionParser[TestToolConfiguration]("detective-test-tool") {
    head("detective-test-tool", "0.1.0")

    opt[Int]('m', "messages")
      .required()
      .valueName("<number>")
      .action( (messages, config) => config.copy(numberOfMessages = messages))

    opt[Int]('s', "seconds")
      .required()
      .valueName("<number>")
      .action( (seconds, config) => config.copy(numberOfSeconds = seconds) )

    opt[String]('b', "bootstrap-server")
      .required()
      .valueName("<broker address>")
      .action( (server, config) => config.copy(bootstrapServer = server) )

    opt[String]("first-topic")
      .required()
      .valueName("<topic>")
      .action( (topic, config) => config.copy(topic1Topic = topic) )

    opt[Int]("first-topic-partition")
      .valueName("<partition>")
      .action( (partition, config) => config.copy(topic1Partition = Some(partition)) )

    opt[String]("second-topic")
      .required()
      .valueName("<topic>")
      .action( (topic, config) => config.copy(topic2Topic = topic) )

    opt[Int]("second-topic-partition")
      .valueName("<partition>")
      .action( (partition, config) => config.copy(topic2Partition = Some(partition)) )

    opt[Double]("repeated-key-probability")
      .valueName("<decimal [0.0...1.0]>")
      .validate( probability => {
        if (probability >= 0.0 && probability <= 1.0) {
          success
        } else {
          failure("Probability values must be in the range [0.0, 1.0]")
        }
      })
      .action( (pobability, config) => config.copy(repeatedKeyProbability = pobability) )

    opt[Double]("lag-probability")
      .valueName("<decimal [0.0...1.0]>")
      .validate( probability => {
        if (probability >= 0.0 && probability <= 1.0) {
          success
        } else {
          failure("Probability values must be in the range [0.0, 1.0]")
        }
      })
      .action( (pobability, config) => config.copy(lagProbability = pobability) )

    opt[Int]("minimum-lag")
      .valueName("<milliseconds>")
      .validate(minLag => {
        if (minLag > 0) {
          success
        } else {
          failure("Minimum lag must be a number greater than 0")
        }
      })
      .action( (minLag, config) => config.copy(minimumLagMilliseconds = minLag) )

    opt[Int]("maximum-lag")
      .valueName("<milliseconds>")
      .validate(maxLag => {
        if (maxLag > 0) {
          success
        } else {
          failure("Maximum lag must be a number greater than 0")
        }
      })
      .action( (maxLag, config) => config.copy(maximumLagMilliseconds = maxLag) )

    opt[Unit]("disable-topic1-lag")
      .action( (_, config) => config.copy(enableTopic1Lag = false) )
    opt[Unit]("disable-topic2-lag")
      .action( (_, config) => config.copy(enableTopic2Lag = false) )

    opt[Double]("uneven-distribution-probability")
      .valueName("<decimal [0.0...1.0]>")
      .validate( probability => {
        if (probability >= 0.0 && probability <= 1.0) {
          success
        } else {
          failure("Probability values must be in the range [0.0, 1.0]")
        }
      })
      .action( (pobability, config) => config.copy(unevenDistributionProbability = pobability) )

    opt[Double]("maximum-distribution-variance")
      .valueName("<decimal [0.0...1.0]>")
      .validate( probability => {
        if (probability >= 0.0 && probability <= 1.0) {
          success
        } else {
          failure("Variance values must be in the range [0.0, 1.0]")
        }
      })
      .action( (pobability, config) => config.copy(maximumDistributionVariance = pobability) )

    opt[Unit]("disable-topic1-uneven")
      .action( (_, config) => config.copy(enableTopic1Uneven = false) )
    opt[Unit]("disable-topic2-uneven")
      .action( (_, config) => config.copy(enableTopic2Uneven = false) )

    help("help").text("Print help text")
  }
  def main(args: Array[String]): Unit = {
    parser.parse(args, TestToolConfiguration()) match {
      case Some(config) =>
        println("Detective TestTool is starting.")

        println(s"Generating ${config.numberOfMessages} messages...")
        val messages: Seq[(Array[Byte], Array[Byte])] = MessageGenerator.generate(
          config.numberOfMessages,
          config.repeatedKeyProbability
        )

        println("Batching messages for topic 1...")
        val topic1Batches: Seq[MessageBatch] = MessageBatcher.batch(
          messages,
          config.numberOfSeconds,
          config.lagProbability,
          config.minimumLagMilliseconds,
          config.maximumLagMilliseconds,
          config.enableTopic1Lag,
          config.unevenDistributionProbability,
          config.maximumDistributionVariance,
          config.enableTopic1Uneven
        )

        println("Batching messages for topic 2...")
        val topic2Batches: Seq[MessageBatch] = MessageBatcher.batch(
          messages,
          config.numberOfSeconds,
          config.lagProbability,
          config.minimumLagMilliseconds,
          config.maximumLagMilliseconds,
          config.enableTopic2Lag,
          config.unevenDistributionProbability,
          config.maximumDistributionVariance,
          config.enableTopic2Uneven
        )

        println("Batching complete... batch details:")

        println("=== TOPIC 1 BATCHING ===")
        for ((batch, index) <- topic1Batches.zipWithIndex) {
          println(s"$index: ${batch.delayMs} ms delay — ${batch.messages.length} messages")
        }

        println("=== TOPIC 2 BATCHING ===")
        for ((batch, index) <- topic2Batches.zipWithIndex) {
          println(s"$index: ${batch.delayMs} ms delay — ${batch.messages.length} messages")
        }

        println(s"Beginning simulation of ${config.numberOfMessages} messages spread over ${config.numberOfSeconds} seconds...")
        SimulationExecutor.execute(
          config.bootstrapServer,
          config.topic1Topic,
          config.topic1Partition,
          config.topic2Topic,
          config.topic2Partition,
          topic1Batches,
          topic2Batches
        )

        println("Simulation concluded.")

      case None =>
    }
  }
}
