package org.gotson.komga.oracle.application.tasks

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.application.tasks.Task
import org.gotson.komga.application.tasks.TaskHandler
import org.gotson.komga.application.tasks.TaskProcessor
import org.gotson.komga.infrastructure.configuration.KomgaSettingsProvider
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.junit.jupiter.api.AfterAll
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder
import org.springframework.context.ApplicationEventPublisher
import java.util.Collections

/** The TaskHandler is a fake (same in KomgaJS) noting the tasks handled and their owner at that time */
class TaskProcessorOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.tasksDao
  private val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
  private val events = mutableListOf<String>()
  private val settings = KomgaSettingsProvider(db.serverSettingsDao, ApplicationEventPublisher { events += it.javaClass.simpleName })

  private val handler =
    mockk<TaskHandler> {
      every { handleTask(any()) } answers {
        val t = firstArg<Task>()
        val owner = OracleDb.query(db.tasksDataSource.connection, "select OWNER from TASK where ID = '${t.uniqueId}'").singleOrNull()?.singleOrNull()
        log += "$t by $owner"
      }
    }

  private val processors = mutableListOf<TaskProcessor>()

  private fun processor() = TaskProcessor(dao, handler, settings, ThreadPoolTaskExecutorBuilder()).also { processors += it }

  private fun q(sql: String) = OracleDb.query(db.tasksDataSource.connection, sql)

  /** fixed modification dates, in the order of the ids given */
  private fun dated(vararg ids: String) = ids.forEachIndexed { i, id -> OracleDb.exec(db.tasksDataSource.connection, "update TASK set LAST_MODIFIED_DATE = '2020-01-01 00:00:0$i' where ID = '$id'") }

  /** waits for the queue to be processed */
  private fun drain(p: TaskProcessor) {
    val end = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < end && (dao.count() > 0 || p.executor.activeCount > 0)) Thread.sleep(5)
  }

  private fun queue() {
    dao.save(
      listOf(
        Task.HashBook("B1", 0),
        Task.AnalyzeBook("B2", 4, "S1"),
        Task.AnalyzeBook("B3", 4, "S1"),
        Task.RefreshSeriesMetadata("S2", 6),
        Task.UpgradeIndex(8),
        Task.DeleteBook("B4", 4),
      ),
    )
    dated("HASH_BOOK_B1", "ANALYZE_BOOK_B3", "ANALYZE_BOOK_B2", "REFRESH_SERIES_METADATA_S2", "UPGRADE_INDEX", "DELETE_BOOK_B4")
  }

  @AfterAll
  fun shutdownExecutors() = processors.forEach { it.executor.shutdown() }

  override fun cases() {
    func("afterPropertiesSet") {
      case("not processing before") { processor().processTasks }
      case("disowns unfinished tasks") {
        dao.save(listOf(Task.HashBook("B1"), Task.HashBook("B2"), Task.HashBook("B3")))
        OracleDb.exec(db.tasksDataSource.connection, "update TASK set OWNER = 'old' where ID <> 'HASH_BOOK_B3'")
        val p = processor()
        p.afterPropertiesSet()
        listOf(p.processTasks, q("select ID, OWNER from TASK order by ID"))
      }
      case("nothing to disown") {
        val p = processor()
        p.afterPropertiesSet()
        p.processTasks.also { dao.deleteAll() }
      }
    }

    func("taskPoolSizeChanged") {
      case("initial pool size") { processor().executor.corePoolSize }
      case("follows the setting") {
        val p = processor()
        settings.taskPoolSize = 3
        p.taskPoolSizeChanged()
        listOf(p.executor.corePoolSize, events)
      }
      case("back to one") {
        val p = processor()
        val before = p.executor.corePoolSize
        settings.taskPoolSize = 1
        p.taskPoolSizeChanged()
        listOf(before, p.executor.corePoolSize)
      }
    }

    func("processAvailableTask") {
      case("not processing") {
        queue()
        val p = processor()
        p.processAvailableTask()
        listOf(log.toList(), dao.count(), p.executor.activeCount).also { dao.deleteAll() }
      }
      case("one thread, by priority") {
        log.clear()
        queue()
        val p = processor()
        p.processTasks = true
        p.processAvailableTask()
        drain(p)
        listOf(log.toList(), dao.count())
      }
    }

    func("takeAndProcess") {
      case("empty queue") {
        log.clear()
        val p = processor()
        p.processTasks = true
        p.processAvailableTask()
        drain(p)
        listOf(log.toList(), dao.count())
      }
      case("task added while processing") {
        log.clear()
        dao.save(Task.DeleteSeries("S1", 1))
        val p = processor()
        p.processTasks = true
        p.processAvailableTask()
        dao.save(Task.DeleteSeries("S2", 1))
        p.processAvailableTask()
        drain(p)
        listOf(log.map { it.substringBefore(" by ") }.sorted(), dao.count())
      }
    }
  }
}
