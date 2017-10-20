package me.frmr.kafka.testtool

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import org.scalacheck.Gen
import org.scalacheck.rng.Seed
import scala.util.Random

object MessageGenerator {
  val messageGen: Gen[JObject] = for {
    field1Str <- Gen.alphaNumStr
    field2Str <- Gen.alphaNumStr
    field3Int <- Gen.posNum[Int]
  } yield {
    ("field1" -> field1Str) ~
    ("field2" -> field2Str) ~
    ("field3" -> field3Int)
  }

  def makeMessage(): Array[Byte] = {
    val seed = Seed.random()

    val msg = messageGen(Gen.Parameters.default, seed)
    compactRender(msg).getBytes("UTF-8")
  }

  def makeKey(): Array[Byte] = {
    val key = Random.alphanumeric.take(32).mkString
    key.getBytes("UTF-8")
  }

  def generate(
    numberOfMessages: Int,
    repeatedKeyProbability: Double
  ): Seq[(Array[Byte], Array[Byte])] = {
    var lastKey: Option[Array[Byte]] = None

    for (messageNumber <- (0 to numberOfMessages)) yield {
      val key = if (lastKey.isDefined && Random.nextDouble() < repeatedKeyProbability) {
        lastKey.get // guarded by isDefined check above
      } else {
        val newKey = makeKey()
        lastKey = Some(newKey)
        newKey
      }

      val message = makeMessage()

      (key, message)
    }
  }
}
