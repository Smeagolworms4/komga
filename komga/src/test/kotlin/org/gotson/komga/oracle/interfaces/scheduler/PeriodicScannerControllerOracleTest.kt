package org.gotson.komga.oracle.interfaces.scheduler

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.application.scheduler.LibraryScanScheduler
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.domain.model.Library
import org.gotson.komga.interfaces.scheduler.PeriodicScannerController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL

class PeriodicScannerControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val calls = mutableListOf<List<Any?>>()
  private val taskEmitter =
    mockk<TaskEmitter>().also {
      every { it.scanLibrary(any(), any(), any()) } answers { calls.add(listOf("scanLibrary", firstArg<String>(), secondArg<Boolean>(), thirdArg<Int>())) }
    }
  private val scheduler =
    mockk<LibraryScanScheduler>().also {
      every { it.scheduleScan(any()) } answers { calls.add(listOf("scheduleScan", firstArg<Library>().id, firstArg<Library>().scanInterval)) }
    }
  private val controller = PeriodicScannerController(taskEmitter, db.libraryDao, scheduler)

  private fun run(block: () -> Unit): List<List<Any?>> {
    calls.clear()
    block()
    return calls.toList()
  }

  override fun cases() {
    func("scanOnStartup") {
      case("no library") { run { controller.scanOnStartup() } }
      case("libraries") {
        db.libraryDao.insert(Library("A", URL("file:/a"), id = "L1", scanOnStartup = true))
        db.libraryDao.insert(Library("B", URL("file:/b"), id = "L2", scanOnStartup = false, scanInterval = Library.ScanInterval.DISABLED))
        db.libraryDao.insert(Library("C", URL("file:/c"), id = "L3", scanOnStartup = true, scanInterval = Library.ScanInterval.WEEKLY))
        run { controller.scanOnStartup() }
      }
    }
    func("scheduleScans") {
      case("all libraries") { run { controller.scheduleScans() } }
    }
  }
}
