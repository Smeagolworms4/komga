package org.gotson.komga.oracle.application.tasks

import org.gotson.komga.application.tasks.Task
import org.gotson.komga.application.tasks.TasksRepository
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class TasksRepositoryOracleTest : OracleTest() {
  private val db = OracleDb()
  private val repository: TasksRepository = db.tasksDao

  private fun owners() = OracleDb.query(db.tasksDataSource.connection, "select ID, OWNER from TASK order by ID")

  override fun cases() {
    func("takeFirst") {
      case("default owner is the current thread name") {
        repository.save(listOf(Task.HashBook("B1", 1), Task.HashBook("B2", 2)))
        val t = repository.takeFirst()
        listOf(t.toString(), owners().map { listOf(it[0], it[1] == Thread.currentThread().name) })
      }
      case("explicit owner") { listOf(repository.takeFirst("me").toString(), owners().map { listOf(it[0], if (it[1] == Thread.currentThread().name) "<current thread>" else it[1]) }) }
      case("empty queue") { repository.takeFirst() }
    }
  }
}
