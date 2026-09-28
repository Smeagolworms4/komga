package org.gotson.komga.oracle.application.scheduler

import io.mockk.mockk
import org.gotson.komga.application.scheduler.LibraryScanScheduler
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.service.BookConverter
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.config.FixedRateTask
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LibraryScanSchedulerOracleTest : OracleTest() {
  /** Records the cancellations (same fake in the TypeScript twin) */
  private class FakeFuture(
    val name: String,
    val log: MutableList<String>,
  ) : ScheduledFuture<Any?> {
    var cancelled = false

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      log.add("cancel $name $mayInterruptIfRunning")
      cancelled = true
      return true
    }

    override fun isCancelled() = cancelled

    override fun isDone() = cancelled

    override fun get(): Any? = null

    override fun get(
      timeout: Long,
      unit: TimeUnit,
    ): Any? = null

    override fun getDelay(unit: TimeUnit): Long = 0

    override fun compareTo(other: Delayed?): Int = 0
  }

  /** Records the fixed rate schedules, runs nothing by itself (same fake in the TypeScript twin) */
  private class FakeScheduler : TaskScheduler {
    val log = mutableListOf<String>()
    val runnables = mutableListOf<Runnable>()

    override fun scheduleAtFixedRate(
      task: Runnable,
      startTime: Instant,
      period: Duration,
    ): ScheduledFuture<*> {
      runnables.add(task)
      val name = "#${runnables.size}"
      log.add("schedule $name $period")
      return FakeFuture(name, log)
    }

    override fun schedule(
      task: Runnable,
      trigger: Trigger,
    ): ScheduledFuture<*>? = throw UnsupportedOperationException()

    override fun schedule(
      task: Runnable,
      startTime: Instant,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleAtFixedRate(
      task: Runnable,
      period: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleWithFixedDelay(
      task: Runnable,
      startTime: Instant,
      delay: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()

    override fun scheduleWithFixedDelay(
      task: Runnable,
      delay: Duration,
    ): ScheduledFuture<*> = throw UnsupportedOperationException()
  }

  private val db = OracleDb()
  private val scheduler = FakeScheduler()
  private val emitter = TaskEmitter(db.bookDao, mockk<BookConverter>(), db.tasksDao, ApplicationEventPublisher { })
  private val libraryScanScheduler = LibraryScanScheduler(scheduler, emitter)

  private fun lib(
    id: String,
    interval: Library.ScanInterval,
  ) = Library("lib $id", URL("file:/lib/$id"), scanInterval = interval, id = id)

  private fun tasks() = libraryScanScheduler.scheduledTasks.map { (it.task as FixedRateTask).intervalDuration }

  override fun cases() {
    func("scheduleScan") {
      case("every 6h") {
        libraryScanScheduler.scheduleScan(lib("L1", Library.ScanInterval.EVERY_6H))
        scheduler.log.toList()
      }
      case("reschedule cancels the previous task") {
        libraryScanScheduler.scheduleScan(lib("L1", Library.ScanInterval.DAILY))
        scheduler.log.toList()
      }
      case("other library") {
        libraryScanScheduler.scheduleScan(lib("L2", Library.ScanInterval.HOURLY))
        listOf(scheduler.log.toList(), tasks().sorted())
      }
      case("disabled cancels and removes") {
        libraryScanScheduler.scheduleScan(lib("L1", Library.ScanInterval.DISABLED))
        listOf(scheduler.log.toList(), tasks())
      }
      case("disabled unknown library") {
        libraryScanScheduler.scheduleScan(lib("L9", Library.ScanInterval.DISABLED))
        scheduler.log.size
      }
      case("scheduled runnable emits a scan task") {
        scheduler.runnables.last().run()
        db.tasksDao.findAll().map { it.toString() }
      }
      case("cancelled runnable still runs when invoked") {
        scheduler.runnables.first().run()
        db.tasksDao.count()
      }
    }
    func("getScheduledTasks") {
      case("current tasks") { tasks() }
      case("new set each call") { libraryScanScheduler.scheduledTasks !== libraryScanScheduler.scheduledTasks }
    }
    func("toDuration") {
      for (interval in Library.ScanInterval.entries.filter { it != Library.ScanInterval.DISABLED }) {
        case(interval.name) {
          libraryScanScheduler.scheduleScan(lib("D", interval))
          scheduler.log.last()
        }
      }
    }
  }
}
