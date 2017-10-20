package me.frmr.kafka.detective.matchtester

import me.frmr.kafka.detective.api._
import java.util.Arrays

/**
 * A [[MonitorMatchTester]] that passes if Object.equals returns true. This class is designed to
 * work with byte arrays ''or'' any class whose `equals` method is configured correctly for checking
 * equality.
 *
 * If neither of these conditions are true, you may need to write your own [[MonitorMatchTester]]
 * for your own needs.
 */
class ValueEqualsTester extends MonitorMatchTester {
  def testMatch(
    testMessage: MonitorObjectEnvelope,
    referenceMessage: MonitorObjectEnvelope
  ): MonitorMatchTestResult = {
    val matches = (testMessage.valueInstance, referenceMessage.valueInstance) match {
      case (testArray: Array[Byte], referenceArray: Array[Byte]) =>
        Arrays.equals(testArray, referenceArray)

      case _ =>
        testMessage.valueInstance.equals(referenceMessage.valueInstance)
    }

    if (matches) {
      SuccessfulMatchTest()
    } else {
      UnsuccessfulMatchTest("Object.equals returned false", testMessage, referenceMessage)
    }
  }
}
