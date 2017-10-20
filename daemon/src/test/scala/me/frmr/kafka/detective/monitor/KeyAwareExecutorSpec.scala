package me.frmr.kafka.detective.monitor

import java.util.concurrent._
import org.scalatest._
import org.scalatest.concurrent._
import scala.util.Random

class KeyAwareExecutorSpec extends FlatSpec with Matchers with Eventually {
  "KeyAwareExecutor" should "submit tasks with unique keys without queueing" in withFixtures { exec =>
    var task1Time: Long = 0L
    val key1 = Random.alphanumeric.take(5).mkString
    val task1 = () => task1Time = System.currentTimeMillis()

    var task2Time: Long = 0L
    val key2 = Random.alphanumeric.take(5).mkString
    val task2 = () => task2Time = System.currentTimeMillis()

    exec.submit(key1, task1)
    exec.submit(key2, task2)

    eventually {
      task1Time should be > 0L
      task2Time should be > 0L

      math.abs(task2Time - task1Time).toInt should be < 100
    }
  }

  it should "execute tasks for the same key in order" in withFixtures { exec =>
    val key = Random.alphanumeric.take(5).mkString

    var task1Time: Long = 0L
    val task1 = () => {
      Thread.sleep(5) //Ensure at least 5ms difference
      task1Time = System.currentTimeMillis()
    }

    var task2Time: Long = 0L
    val task2 = () => {
      Thread.sleep(5) // Ensure at least 5ms difference
      task2Time = System.currentTimeMillis()
    }

    exec.submit(key, task1)
    exec.submit(key, task2)

    eventually {
      task1Time should be > 0L
      task2Time should be > 0L

      math.abs(task2Time - task1Time).toInt should be >= 5
      task2Time.toInt should be > task1Time.toInt
    }
  }

  it should "order tasks for the same key, without preventing execution of other tasks" in withFixtures { exec =>
    val key1and2 = Random.alphanumeric.take(5).mkString
    var task1Time: Long = 0L
    val task1 = () => {
      Thread.sleep(50) //Ensure at least 5ms difference
      task1Time = System.currentTimeMillis()
    }
    var task2Time: Long = 0L
    val task2 = () => {
      Thread.sleep(5) // Ensure at least 5ms difference
      task2Time = System.currentTimeMillis()
    }

    val key3 = Random.alphanumeric.take(5).mkString
    var task3Time: Long = 0L
    val task3 = () => {
      task3Time = System.currentTimeMillis()
    }

    val key4 = Random.alphanumeric.take(5).mkString
    var task4Time: Long = 0L
    val task4 = () => {
      task4Time = System.currentTimeMillis()
    }

    exec.submit(key1and2, task1)
    exec.submit(key1and2, task2)
    exec.submit(key3, task3)
    exec.submit(key4, task4)

    eventually {
      task1Time should be > 0L
      task2Time should be > 0L
      task3Time should be > 0L
      task4Time should be > 0L

      task2Time should be > task1Time
      task2Time should be > task3Time
      task2Time should be > task4Time
    }
  }

  it should "continue executing subsequent tasks for a key if one task throws an exception" in withFixtures { exec =>
    val key = Random.alphanumeric.take(5).mkString

    val task1 = () => {
      throw new RuntimeException("I'm a test exception, just ignore me.")
    }

    var task2Time: Long = 0L
    val task2 = () => {
      Thread.sleep(5) // Ensure at least 5ms difference
      task2Time = System.currentTimeMillis()
    }

    exec.submit(key, task1)
    exec.submit(key, task2)

    eventually {
      task2Time should be > 0L
    }
  }

  def withFixtures(testCode: KeyAwareExecutor=>Any) = {
    val workQueue: BlockingQueue[Runnable] = new LinkedBlockingQueue[Runnable]()
    val executor = new KeyAwareExecutor(5, 5, 5, TimeUnit.MINUTES, workQueue)

    try {
      testCode(executor)
    } finally {
      executor.shutdownNow()
    }
  }
}
