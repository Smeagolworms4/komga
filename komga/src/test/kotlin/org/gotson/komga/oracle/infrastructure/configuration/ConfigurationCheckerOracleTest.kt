package org.gotson.komga.oracle.infrastructure.configuration

import org.gotson.komga.infrastructure.configuration.ConfigurationChecker
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** Only local file systems are available to the tests: every check passes (Unit) */
class ConfigurationCheckerOracleTest : OracleTest() {
  private fun check(
    database: String,
    tasks: String = database,
    checkDatabase: Boolean = true,
    checkTasks: Boolean = true,
  ): Any? =
    try {
      ConfigurationChecker(
        KomgaProperties().apply {
          this.database.file = database
          this.database.checkLocalFilesystem = checkDatabase
          this.tasksDb.file = tasks
          this.tasksDb.checkLocalFilesystem = checkTasks
        },
      ).checkDatabasesPath()
    } catch (e: Exception) {
      listOf(e::class.simpleName, e.message?.replace(tempDir.toString(), "<tmp>"))
    }

  override fun cases() {
    val existing = tempDir.resolve("db").createDirectories().resolve("database.sqlite").apply { writeText("") }.toString()
    val missing = tempDir.resolve("db/missing.sqlite").toString()
    func("checkDatabasesPath") {
      case("existing files") { check(existing) }
      case("missing file, existing parent") { check(missing) }
      case("missing parent") { check(tempDir.resolve("nope/nope/db.sqlite").toString()) }
      case("checks disabled") { check(existing, checkDatabase = false, checkTasks = false) }
      case("default empty file") { check("") }
    }
    func("checkDatabaseIsLocal") {
      case("relative path without parent") { check("database.sqlite") }
      case("memory database") { check(":memory:") }
      case("file uri") { check("file:$existing?mode=ro") }
      case("nul character") { check("a\u0000b") }
      case("only tasks checked") { check(existing, missing, checkDatabase = false) }
    }
    func("checkIfRemote") {
      case("directory") { check(tempDir.toString()) }
      case("root") { check("/") }
      case("proc file") { check("/proc/self/mounts") }
    }
  }
}
