package me.frmr.kafka.detective.util

import java.util.concurrent._
import java.util.concurrent.atomic._

class PrefixNamedThreadFactory(prefix: String) extends ThreadFactory {
  private[this] var threadNumber = new AtomicInteger(0)

  private[this] def threadName: String = {
    val currentNumber = threadNumber.getAndIncrement()
    s"$prefix-$currentNumber"
  }

  def newThread(runnable: Runnable): Thread = {
    new Thread(runnable, threadName)
  }
}
