package org.gotson.komga.oracle.infrastructure.util

import org.gotson.komga.infrastructure.util.checkTempDirectory
import org.gotson.komga.oracle.OracleTest
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class TempDirectoryCheckerOracleTest : OracleTest() {
  /** Runs [block] with java.io.tmpdir set to [dir] (os.tmpdir() / TMPDIR in KomgaJS), temp paths hidden in messages */
  private fun withTmp(
    dir: String,
    block: () -> Any?,
  ): Any? {
    val previous = System.getProperty("java.io.tmpdir")
    System.setProperty("java.io.tmpdir", dir)
    try {
      return block()
    } catch (e: Exception) {
      return listOf(e::class.simpleName, e.message?.replace(tempDir.toString(), "<tmp>"))
    } finally {
      System.setProperty("java.io.tmpdir", previous)
    }
  }

  override fun cases() {
    func("checkTempDirectory") {
      case("existing directory") { withTmp(tempDir.resolve("existing").createDirectories().toString()) { checkTempDirectory() } }
      case("missing directory is created") {
        val dir = tempDir.resolve("missing/nested dir")
        listOf(withTmp(dir.toString()) { checkTempDirectory() }, dir.exists(), Files.isDirectory(dir))
      }
      case("trailing slash") { withTmp(tempDir.resolve("slash").toString() + "/") { checkTempDirectory() } }
      case("cannot be created under a file") {
        val f = tempDir.resolve("a-file").apply { writeText("x") }
        withTmp(f.resolve("sub").toString()) { checkTempDirectory() }
      }
      case("not writable") {
        val ro = tempDir.resolve("read-only").createDirectories()
        ro.toFile().setWritable(false, false)
        try {
          withTmp(ro.toString()) { checkTempDirectory() }
        } finally {
          ro.toFile().setWritable(true, false)
        }
      }
      case("unicode directory") { withTmp(tempDir.resolve("dossier été 漫画").toString()) { checkTempDirectory() } }
    }
  }
}
