package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.application.tasks.Task
import org.gotson.komga.interfaces.api.rest.TaskController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class TaskControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val controller = TaskController(db.tasksDao)

  override fun cases() {
    func("emptyTaskQueue") {
      case("empty") { controller.emptyTaskQueue() }
      case("tasks without owner") {
        db.tasksDao.save(Task.ScanLibrary("L1", false))
        db.tasksDao.save(Task.EmptyTrash("L1"))
        db.tasksDao.save(Task.RefreshSeriesMetadata("S1"))
        listOf(controller.emptyTaskQueue(), db.tasksDao.count())
      }
      case("owned tasks are kept") {
        db.tasksDao.save(Task.ScanLibrary("L1", true))
        db.tasksDao.save(Task.EmptyTrash("L2"))
        db.tasksDao.takeFirst("worker-1")
        listOf(controller.emptyTaskQueue(), db.tasksDao.findAll().map { it.toString() })
      }
    }
  }
}
