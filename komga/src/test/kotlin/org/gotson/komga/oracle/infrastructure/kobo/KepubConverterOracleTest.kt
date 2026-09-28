package org.gotson.komga.oracle.infrastructure.kobo

import org.gotson.komga.domain.model.BookWithMedia
import org.gotson.komga.infrastructure.kobo.KepubConverter
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.gotson.komga.oracle.interfaces.InterfacesServices
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class KepubConverterOracleTest : OracleTest() {
  private val db = OracleDb()
  private val services = InterfacesServices(db)

  private fun script(
    name: String,
    content: String,
    executable: Boolean = true,
  ): String {
    val p = tempDir.resolve("bin").createDirectories().resolve(name)
    p.writeText("#!/bin/sh\n$content\n")
    if (executable) Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"))
    return p.toString()
  }

  private val copy by lazy { script("kepubify-copy", "cp \"$1\" \"$3\"\necho converted") }
  private val failing by lazy { script("kepubify-fail", "echo boom >&2\nexit 3") }
  private val noOutput by lazy { script("kepubify-none", "exit 0") }
  private val notExecutable by lazy { script("kepubify-noexec", "exit 0", executable = false) }

  private fun clean(s: String?) = s?.replace(tempDir.toString(), "<tmp>")?.replace(Path.of(System.getProperty("java.io.tmpdir")).toString(), "<systmp>")

  private fun state(c: KepubConverter) = listOf(c.isAvailable, clean(c.kepubifyPath?.toString()))

  private fun <T> attempt(block: () -> T): Any? =
    try {
      block()
    } catch (e: Exception) {
      listOf(e::class.java.simpleName, clean(e.message))
    }

  private fun converter(configPath: String? = null) = KepubConverter(services.settings, db.bookProjectionDao, configPath)

  private fun call(
    c: KepubConverter,
    name: String,
    vararg args: Any?,
  ): Any? {
    val m = KepubConverter::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
    m.isAccessible = true
    return try {
      m.invoke(c, *args)
    } catch (e: java.lang.reflect.InvocationTargetException) {
      throw e.targetException
    }
  }

  private fun describe(p: Path?) = p?.let { listOf(it.fileName.toString(), it.exists(), it.exists() && it.readBytes().contentEquals(tempDir.resolve("real.epub").readBytes()), clean(it.parent.toString())) }

  private fun projections() = db.rawQuery("SELECT BOOK_ID, PROFILE, FILE_SIZE FROM BOOK_PROJECTION ORDER BY BOOK_ID, PROFILE")

  private fun bookWithMedia(id: String) = BookWithMedia(db.bookDao.findByIdOrNull(id)!!, db.mediaDao.findById(id))

  override fun cases() {
    func("configureKepubify") {
      case("setup") {
        InterfacesData.setup(db)
        InterfacesData.realBooks(db, tempDir)
      }
      case("null") { converter().also { it.configureKepubify(null) }.let { state(it) } }
      case("blank") { converter().also { it.configureKepubify("  ") }.let { state(it) } }
      case("executable script") { converter().also { it.configureKepubify(copy) }.let { state(it) } }
      case("missing file") { converter().also { it.configureKepubify("/oracle-missing/kepubify") }.let { state(it) } }
      case("not executable file") { converter().also { it.configureKepubify(notExecutable) }.let { state(it) } }
      case("command in PATH, exit 0") { converter().also { it.configureKepubify("true") }.let { state(it) } }
      case("command in PATH, exit 1") { converter().also { it.configureKepubify("false") }.let { state(it) } }
      case("null with fallback") { converter(copy).also { it.configureKepubify(null, true) }.let { state(it) } }
      case("invalid with fallback") { converter(copy).also { it.configureKepubify("/oracle-missing/k", true) }.let { state(it) } }
      case("invalid with invalid fallback") { converter("/oracle-missing/k2").also { it.configureKepubify("/oracle-missing/k", true) }.let { state(it) } }
      case("null without fallback") { converter(copy).also { it.configureKepubify(null) }.let { state(it) } }
      case("valid then null") {
        converter().also {
          it.configureKepubify(copy)
          it.configureKepubify(null)
        }.let { state(it) }
      }
    }
    func("configureKepubifyOnStartup") {
      case("nothing set") { converter().also { call(it, "configureKepubifyOnStartup") }.let { state(it) } }
      case("configuration path") { converter(copy).also { call(it, "configureKepubifyOnStartup") }.let { state(it) } }
      case("setting wins") {
        services.settings.kepubifyPath = failing
        converter(copy).also { call(it, "configureKepubifyOnStartup") }.let { state(it) }
      }
      case("blank setting") {
        services.settings.kepubifyPath = " "
        converter(copy).also { call(it, "configureKepubifyOnStartup") }.let { state(it) }
      }
    }
    func("configureKepubifyOnSettingsChange") {
      case("setting") {
        services.settings.kepubifyPath = copy
        converter().also { call(it, "configureKepubifyOnSettingsChange") }.let { state(it) }
      }
      case("setting removed, fallback") {
        services.settings.kepubifyPath = null
        converter(noOutput).also { call(it, "configureKepubifyOnSettingsChange") }.let { state(it) }
      }
      case("events") { services.drainEvents() }
    }
    func("isExecutable") {
      val c by lazy { converter() }
      case("script") { call(c, "isExecutable", Path.of(copy)) }
      case("not executable") { call(c, "isExecutable", Path.of(notExecutable)) }
      case("missing") { call(c, "isExecutable", Path.of("/oracle-missing/x")) }
      case("directory") { call(c, "isExecutable", tempDir) }
      case("true") { call(c, "isExecutable", Path.of("true")) }
      case("false") { call(c, "isExecutable", Path.of("false")) }
    }
    func("convertEpubToKepub") {
      val c by lazy { converter(copy).also { it.configureKepubify(copy) } }
      case("epub") { attempt { describe(c.convertEpubToKepub(bookWithMedia("B8"))) } }
      case("projection saved") { projections() }
      case("to directory") { attempt { describe(c.convertEpubToKepub(bookWithMedia("B8"), tempDir.resolve("out").createDirectories())) } }
      case("not an epub") { attempt { c.convertEpubToKepub(bookWithMedia("B7")) } }
      case("already kepub") { attempt { c.convertEpubToKepub(bookWithMedia("B8").let { it.copy(media = it.media.copy(epubIsKepub = true)) }) } }
      case("missing file") { attempt { c.convertEpubToKepub(bookWithMedia("B4")) } }
      case("not available") { attempt { converter().convertEpubToKepub(bookWithMedia("B8")) } }
    }
    func("convertEpubToKepubWithoutChecks") {
      case("missing destination") { attempt { converter().also { it.configureKepubify(copy) }.convertEpubToKepubWithoutChecks(bookWithMedia("B8").book, tempDir.resolve("nope")) } }
      case("failing converter") { attempt { converter().also { it.configureKepubify(failing) }.convertEpubToKepubWithoutChecks(bookWithMedia("B8").book) } }
      case("no output") { attempt { converter().also { it.configureKepubify(noOutput) }.convertEpubToKepubWithoutChecks(bookWithMedia("B8").book, tempDir) } }
      case("cbz source copied") { attempt { describe(converter().also { it.configureKepubify(copy) }.convertEpubToKepubWithoutChecks(bookWithMedia("B7").book, tempDir.resolve("out"))) } }
      case("missing source") { attempt { converter().also { it.configureKepubify(copy) }.convertEpubToKepubWithoutChecks(bookWithMedia("B4").book, tempDir.resolve("out")) } }
      case("not available") { attempt { converter().convertEpubToKepubWithoutChecks(bookWithMedia("B8").book) } }
      case("projections") { projections() }
    }
  }
}
