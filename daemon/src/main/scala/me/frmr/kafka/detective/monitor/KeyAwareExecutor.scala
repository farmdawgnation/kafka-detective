package me.frmr.kafka.detective.monitor

import me.frmr.kafka.detective.util.PrefixNamedThreadFactory
import com.typesafe.scalalogging.StrictLogging
import java.util._
import java.util.concurrent._
import scala.util.control.NonFatal

/**
 * A KeyAwareExecutor that will only execute at most one task at a time for each key. Any additional
 * tasks that come in for the same keyonce `maxActiveTasksPerKey` is reached, will be queued and
 * started as tasks for the maxed-out key complete.
 *
 * This thin wrapper around `ThreadPoolExecutor` ensures we can handle items with the same key in
 * the order they are seen by the messaging system because we don't queue the subsequent occurences
 * of a key inside the `ThreadPoolExecutor` until the currently queued occurence of that key is
 * finished running.
 */
class KeyAwareExecutor(
  corePoolSize: Int,
  maximumPoolSize: Int,
  keepAliveTime: Long,
  unit: TimeUnit,
  workQueue: BlockingQueue[Runnable],
  threadNamePrefix: String = "key-aware-executor"
) extends StrictLogging {
  private[this] val activeTasks = new ConcurrentHashMap[String, Int]()
  private[this] val queuedTasks = new ConcurrentHashMap[String, Array[Callable[String]]]()

  private[this] val maxActiveTasksPerKey = 1

  private[this] val threadPoolExecutor = new ThreadPoolExecutor(
    corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    unit,
    workQueue,
    new PrefixNamedThreadFactory(threadNamePrefix)
  ) {
    override def afterExecute(finishedRunnable: Runnable, error: Throwable): Unit = {
      val keyForThisRunnable =
        finishedRunnable.asInstanceOf[FutureTask[String]].get()

      handleTaskFinished(keyForThisRunnable)
    }
  }

  def handleTaskFinished(keyForThisRunnable: String): Unit = {
    queuedTasks.synchronized {
      val queuedTasksForThisKey = queuedTasks.get(keyForThisRunnable)

      if (queuedTasksForThisKey != null && queuedTasksForThisKey.length == 1) {
        queuedTasks.remove(keyForThisRunnable)
        threadPoolExecutor.submit(queuedTasksForThisKey.head)
      } else if (queuedTasksForThisKey != null && queuedTasksForThisKey.length > 1) {
        val nextRunnable = queuedTasksForThisKey.head
        val updatedQueue = queuedTasksForThisKey.drop(1)
        queuedTasks.put(keyForThisRunnable, updatedQueue)
        threadPoolExecutor.submit(nextRunnable)
      } else {
        activeTasks.synchronized {
          val activeTasksForKey = activeTasks.getOrDefault(keyForThisRunnable, 0)

          if (activeTasksForKey > 1) {
            activeTasks.put(keyForThisRunnable, activeTasksForKey-1)
          } else {
            activeTasks.remove(keyForThisRunnable)
          }
        }
      }
    }
  }

  def submit(key: String, task: () => Unit): Unit = {
    val callable: Callable[String] = () => {
      try {
        task()
      } catch {
        case NonFatal(exception) =>
          logger.error(s"Exception executing task for $key", exception)
      }

      key
    }

    val activeTasksForKey = activeTasks.getOrDefault(key, 0)

    if (activeTasksForKey < maxActiveTasksPerKey) {
      activeTasks.put(key, activeTasksForKey+1)
      threadPoolExecutor.submit(callable)
    } else {
      queuedTasks.synchronized {
        val existingQueuedTasks = queuedTasks.getOrDefault(key, Array[Callable[String]]())
        val newQueuedTasks: Array[Callable[String]] = existingQueuedTasks :+ callable
        queuedTasks.put(key, newQueuedTasks)
      }
    }
  }

  def getQueue(): BlockingQueue[Runnable] =
    workQueue
  def shutdownNow(): List[Runnable] =
    threadPoolExecutor.shutdownNow()
  def shutdown(): Unit =
    threadPoolExecutor.shutdown()
  def isTerminated(): Boolean =
    threadPoolExecutor.isTerminated()
  def isShutdown(): Boolean =
    threadPoolExecutor.isShutdown()
  def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
    threadPoolExecutor.awaitTermination(timeout, unit)
}
