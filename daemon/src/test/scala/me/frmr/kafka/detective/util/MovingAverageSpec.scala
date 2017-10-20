package me.frmr.kafka.detective.util

import org.scalatest._

class MovingAverageSpec extends FlatSpec with Matchers {
  "MovingAverage" should "correctly compute moving averages of 1" in {
    val mAvg = new MovingAverage(5)
    mAvg.addSample(100)

    mAvg.getCurrentAverage should be (100)
  }

  it should "correctly compute a few values" in {
    val mAvg = new MovingAverage(5)
    mAvg.addSample(100)
    mAvg.addSample(200)

    mAvg.getCurrentAverage should be (150)
  }

  it should "correclty compute more than the window values" in {
    val mAvg = new MovingAverage(5)
    mAvg.addSample(100)
    mAvg.addSample(200)
    mAvg.addSample(300)
    mAvg.addSample(400)
    mAvg.addSample(500)
    mAvg.addSample(600)

    mAvg.getCurrentAverage should be (400)
  }
}
