package org.gotson.komga.oracle.infrastructure.util

import org.apache.commons.compress.archivers.zip.ZipFile
import org.gotson.komga.infrastructure.util.getEntryBytes
import org.gotson.komga.infrastructure.util.getEntryInputStream
import org.gotson.komga.infrastructure.util.getZipEntryBytes
import org.gotson.komga.infrastructure.util.use
import org.gotson.komga.oracle.OracleTest
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.CRC32
import kotlin.io.path.writeBytes

class ZipFileUtilsOracleTest : OracleTest() {
  private val resources = Path.of("src/test/resources/archives")

  private class Entry(
    val rawName: String,
    val data: ByteArray,
    val localUnicode: String? = null,
    val centralUnicode: String? = null,
    val utf8: Boolean = false,
  )

  /** Stored zip written byte by byte (same writer in the TypeScript twin), with optional Unicode Path extra fields */
  private fun zip(vararg entries: Entry): ByteArray {
    val out = ByteArrayOutputStream()
    val central = ByteArrayOutputStream()

    fun ByteArrayOutputStream.u16(v: Int) = apply { write(v and 0xff); write((v shr 8) and 0xff) }

    fun ByteArrayOutputStream.u32(v: Long) = apply { u16((v and 0xffff).toInt()); u16(((v shr 16) and 0xffff).toInt()) }

    fun crc(b: ByteArray) = CRC32().apply { update(b) }.value

    fun unicodeExtra(
      raw: ByteArray,
      name: String?,
    ): ByteArray {
      if (name == null) return ByteArray(0)
      val utf = name.toByteArray(Charsets.UTF_8)
      return ByteArrayOutputStream().u16(0x7075).u16(5 + utf.size).apply { write(1) }.u32(crc(raw)).apply { write(utf) }.toByteArray()
    }
    for (e in entries) {
      val raw = e.rawName.toByteArray(Charsets.UTF_8)
      val flags = if (e.utf8) 0x800 else 0
      val offset = out.size().toLong()
      val localExtra = unicodeExtra(raw, e.localUnicode)
      out.u32(0x04034b50).u16(10).u16(flags).u16(0).u16(0).u16(0x21)
      out.u32(crc(e.data)).u32(e.data.size.toLong()).u32(e.data.size.toLong()).u16(raw.size).u16(localExtra.size)
      out.write(raw)
      out.write(localExtra)
      out.write(e.data)
      val centralExtra = unicodeExtra(raw, e.centralUnicode)
      central.u32(0x02014b50).u16(20).u16(10).u16(flags).u16(0).u16(0).u16(0x21)
      central.u32(crc(e.data)).u32(e.data.size.toLong()).u32(e.data.size.toLong()).u16(raw.size).u16(centralExtra.size)
      central.u16(0).u16(0).u16(0).u32(0).u32(offset)
      central.write(raw)
      central.write(centralExtra)
    }
    val cdOffset = out.size().toLong()
    val cd = central.toByteArray()
    out.write(cd)
    out.u32(0x06054b50).u16(0).u16(0).u16(entries.size).u16(entries.size).u32(cd.size.toLong()).u32(cdOffset).u16(0)
    return out.toByteArray()
  }

  private fun file(
    name: String,
    bytes: ByteArray,
  ): Path = tempDir.resolve(name).also { it.writeBytes(bytes) }

  override fun cases() {
    val simple =
      file(
        "simple.zip",
        zip(
          Entry("a.txt", "hello".toByteArray()),
          Entry("dir/b.bin", oracleBytes(300)),
          Entry("empty", ByteArray(0)),
          Entry("été.txt", "utf8".toByteArray(), utf8 = true),
          Entry("dir/", ByteArray(0)),
        ),
      )
    val unicodeLocal = file("unicode-local.zip", zip(Entry("a_.txt", "local".toByteArray(), localUnicode = "aé.txt")))
    val unicodeCentral = file("unicode-central.zip", zip(Entry("b_.txt", "central".toByteArray(), centralUnicode = "bü.txt")))
    val duplicate = file("duplicate.zip", zip(Entry("x", "first".toByteArray()), Entry("x", "second".toByteArray())))
    val notZip = file("not-a-zip.zip", oracleBytes(100))
    val emptyFile = file("empty.zip", ByteArray(0))

    func("use") {
      case("entry names") { ZipFile.builder().setPath(simple).use { zip -> zip.entries.toList().map { it.name } } }
      case("result of block") { ZipFile.builder().setPath(simple).use { 42 } }
      case("exception in block") { ZipFile.builder().setPath(simple).use { throw IllegalStateException("inside") } }
      case("missing file") { exceptionType { ZipFile.builder().setPath(tempDir.resolve("missing.zip")).use { it.entries.toList().size } } }
      case("not a zip") { exceptionType { ZipFile.builder().setPath(notZip).use { it.entries.toList().size } } }
      case("empty file") { exceptionType { ZipFile.builder().setPath(emptyFile).use { it.entries.toList().size } } }
      case("unicode extra field in central directory") {
        ZipFile.builder().setPath(unicodeCentral).setUseUnicodeExtraFields(true).use { zip -> zip.entries.toList().map { it.name } }
      }
      case("unicode extra field ignored") {
        ZipFile.builder().setPath(unicodeCentral).setUseUnicodeExtraFields(false).use { zip -> zip.entries.toList().map { it.name } }
      }
    }

    func("getEntryInputStream") {
      case("existing") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("a.txt")?.use { it.readBytes() } } }
      case("in directory") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("dir/b.bin")?.use { it.readBytes().size } } }
      case("missing") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("nope") } }
      case("case sensitive") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("A.TXT") } }
      case("empty name") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("") } }
      case("directory entry") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("dir/")?.use { it.readBytes() } } }
      case("directory without slash") { ZipFile.builder().setPath(simple).use { zip -> zip.getEntryInputStream("dir") } }
      case("duplicate name") { ZipFile.builder().setPath(duplicate).use { zip -> zip.getEntryInputStream("x")?.use { it.readBytes() } } }
    }

    func("getEntryBytes") {
      case("existing") { ZipFile.builder().setPath(simple).use { it.getEntryBytes("a.txt") } }
      case("empty entry") { ZipFile.builder().setPath(simple).use { it.getEntryBytes("empty") } }
      case("utf8 name") { ZipFile.builder().setPath(simple).use { it.getEntryBytes("été.txt") } }
      case("missing") { ZipFile.builder().setPath(simple).use { it.getEntryBytes("b.bin") } }
      case("resource png size") { ZipFile.builder().setPath(resources.resolve("zip.zip")).use { it.getEntryBytes("komga.png")?.size } }
      case("encrypted") { exceptionType { ZipFile.builder().setPath(resources.resolve("zip-encrypted.zip")).use { it.getEntryBytes("komga.png") } } }
    }

    func("getZipEntryBytes") {
      case("fast path") { getZipEntryBytes(simple, "a.txt") }
      case("fast path, binary") { getZipEntryBytes(simple, "dir/b.bin") }
      case("empty entry") { getZipEntryBytes(simple, "empty") }
      case("utf8 flag name") { getZipEntryBytes(simple, "été.txt") }
      case("slow path, unicode name in local header") { getZipEntryBytes(unicodeLocal, "aé.txt") }
      case("raw name, unicode in local header") { getZipEntryBytes(unicodeLocal, "a_.txt") }
      case("unicode name in central directory") { getZipEntryBytes(unicodeCentral, "bü.txt") }
      case("raw name, unicode in central directory") { getZipEntryBytes(unicodeCentral, "b_.txt") }
      case("missing entry") { getZipEntryBytes(simple, "nope.txt") }
      case("missing entry, unicode") { getZipEntryBytes(simple, "漫画.png") }
      case("duplicate name") { getZipEntryBytes(duplicate, "x") }
      case("missing file") { exceptionType { getZipEntryBytes(tempDir.resolve("missing.zip"), "a.txt") } }
      case("not a zip") { exceptionType { getZipEntryBytes(notZip, "a.txt") } }
      case("resource zip") { getZipEntryBytes(resources.resolve("zip.zip"), "komga.png").size }
      case("resource copy") { getZipEntryBytes(resources.resolve("zip-copy.zip"), "komga.png").contentEquals(getZipEntryBytes(resources.resolve("zip.zip"), "komga.png")) }
      case("resource bzip2") { getZipEntryBytes(resources.resolve("zip-bzip2.zip"), "komga.png").size }
      case("resource deflate64") { getZipEntryBytes(resources.resolve("zip-deflate64.zip"), "komga.png").size }
      case("resource encrypted") { exceptionType { getZipEntryBytes(resources.resolve("zip-encrypted.zip"), "komga.png") } }
      case("resource lzma") { exceptionType { getZipEntryBytes(resources.resolve("zip-lzma.zip"), "komga.png") } }
      case("resource ppmd") { exceptionType { getZipEntryBytes(resources.resolve("zip-ppmd.zip"), "komga.png") } }
    }

    func("getEntryBytesClosing") {
      case("found in central directory") { getZipEntryBytes(unicodeCentral, "bü.txt").size }
      case("not found in either") { exceptionType { getZipEntryBytes(unicodeLocal, "zzz") } }
    }
  }
}
