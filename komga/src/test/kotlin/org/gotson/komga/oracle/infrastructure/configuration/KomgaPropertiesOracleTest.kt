package org.gotson.komga.oracle.infrastructure.configuration

import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.writeText

class KomgaPropertiesOracleTest : OracleTest() {
  private fun makeDirs(
    database: String,
    tasks: String,
  ): Any? {
    val p =
      KomgaProperties().apply {
        this.database.file = database
        this.tasksDb.file = tasks
      }
    KomgaProperties::class.java.getDeclaredMethod("makeDirs").apply { isAccessible = true }.invoke(p)
    return Unit
  }

  override fun cases() {
    func("makeDirs") {
      case("both parents created") {
        val r = makeDirs(tempDir.resolve("a/b/database.sqlite").toString(), tempDir.resolve("c/tasks.sqlite").toString())
        listOf(r, tempDir.resolve("a/b").isDirectory(), tempDir.resolve("c").isDirectory(), tempDir.resolve("a/b/database.sqlite").exists())
      }
      case("existing parents") { makeDirs(tempDir.resolve("a/b/database.sqlite").toString(), tempDir.resolve("a/tasks.sqlite").toString()) }
      case("no parent stops silently") {
        val r = makeDirs("database.sqlite", tempDir.resolve("d/tasks.sqlite").toString())
        listOf(r, tempDir.resolve("d").exists())
      }
      case("empty file names") { makeDirs("", "") }
      case("parent is a file") {
        tempDir.resolve("file").writeText("x")
        val r = makeDirs(tempDir.resolve("file/sub/database.sqlite").toString(), tempDir.resolve("e/tasks.sqlite").toString())
        listOf(r, tempDir.resolve("e").exists())
      }
      case("memory database") {
        val r = makeDirs(":memory:", tempDir.resolve("f/g/tasks.sqlite").toString())
        listOf(r, tempDir.resolve("f/g").isDirectory())
      }
      case("unicode path") {
        val r = makeDirs(tempDir.resolve("données 漫画/db.sqlite").toString(), tempDir.resolve("données 漫画/t.sqlite").toString())
        listOf(r, tempDir.resolve("données 漫画").isDirectory())
      }
    }
  }
}
