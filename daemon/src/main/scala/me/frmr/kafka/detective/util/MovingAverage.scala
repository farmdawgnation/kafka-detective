package me.frmr.kafka.detective.util

class MovingAverage(sampleSize: Int = 5) {
  private[this] var values: List[Long] = List()

  @volatile
  private[this] var currentAverage: Long = 0L

  def addSample(value: Long): Unit = {
    val newValues = value +: values

    val limitedValues = if (newValues.lengthCompare(sampleSize) > 0) {
      newValues.take(sampleSize)
    } else {
      newValues
    }

    values = limitedValues

    currentAverage = values.foldLeft(0L)(_ + _) / values.length
  }

  def getCurrentAverage: Long = currentAverage
}
