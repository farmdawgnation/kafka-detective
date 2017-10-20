package me.frmr.kafka.testtool

import scala.math
import scala.util.Random

object MessageBatcher {
  private[this] def sliding[T](
    collection: Seq[T],
    slotSize: Int,
    unevenProbability: Double,
    maximumUnevenDist: Double,
    unevenEnabled: Boolean
  ): Seq[Seq[T]] = {
    def takeMore = Random.nextDouble() < 0.5
    def adjustTakeBy = (slotSize*math.max(Random.nextDouble(), maximumUnevenDist)).toInt

    var numberTaken = 0
    val slotCount = collection.size / slotSize

    for (slot <- (0 to slotCount)) yield {
      if (slot == slotCount) {
        collection.drop(numberTaken)
      } else if (unevenEnabled && Random.nextDouble() < unevenProbability) {
        val numToTake = if (takeMore) {
          slotSize + adjustTakeBy
        } else {
          slotSize - adjustTakeBy
        }

        val nextSlot = collection.drop(numberTaken).take(numToTake)
        numberTaken = numberTaken + numToTake
        nextSlot
      } else {
        val nextSlot = collection.drop(numberTaken).take(slotSize)
        numberTaken = numberTaken + slotSize
        nextSlot
      }
    }
  }

  def batch(
    messages: Seq[(Array[Byte], Array[Byte])],
    duration: Int,
    lagProbability: Double,
    minimumLagMs: Int,
    maximumLagMs: Int,
    lagEnabled: Boolean,
    unevenDistributionProbability: Double,
    maximumDistributionVariance: Double,
    unevenEnabled: Boolean
  ): Seq[MessageBatch] = {
    val numberOfMessages = messages.length
    val messagesPerSecond = messages.length / duration
    val batchedMessages = sliding(
      messages,
      messagesPerSecond,
      unevenDistributionProbability,
      maximumDistributionVariance,
      unevenEnabled
    )
    val baseDistanceMs = 1000 /// one second

    for (batch <- batchedMessages) yield {
      if (lagEnabled && Random.nextDouble() < lagProbability) {
        val lag = Random.nextInt(maximumLagMs - minimumLagMs) + minimumLagMs
        MessageBatch(baseDistanceMs+lag, batch)
      } else {
        MessageBatch(baseDistanceMs, batch)
      }
    }
  }
}
