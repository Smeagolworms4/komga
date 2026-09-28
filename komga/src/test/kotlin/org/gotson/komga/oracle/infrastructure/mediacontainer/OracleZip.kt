package org.gotson.komga.oracle.infrastructure.mediacontainer

import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.CRC32
import kotlin.io.path.writeBytes

/**
 * Minimal deterministic ZIP writer for the oracle tests (same bytes as test/unit/infrastructure/mediacontainer/oracleZip.ts
 * in KomgaJS): STORED entries only, fixed DOS date (1980-01-01 00:00), no extra field, UTF-8 flag on every entry.
 * An entry whose content is null is written as a directory (its name should end with '/').
 */
object OracleZip {
  fun bytes(entries: List<Pair<String, ByteArray?>>): ByteArray {
    val out = ByteArrayOutputStream()
    val central = ByteArrayOutputStream()

    fun ByteArrayOutputStream.u16(v: Int) {
      write(v and 0xff)
      write((v shr 8) and 0xff)
    }

    fun ByteArrayOutputStream.u32(v: Long) {
      for (i in 0 until 4) write(((v shr (8 * i)) and 0xff).toInt())
    }
    for ((name, content) in entries) {
      val data = content ?: ByteArray(0)
      val nameBytes = name.toByteArray(Charsets.UTF_8)
      val crc = CRC32().also { it.update(data) }.value
      val offset = out.size().toLong()
      out.u32(0x04034b50)
      out.u16(10)
      out.u16(0x0800)
      out.u16(0)
      out.u16(0)
      out.u16(0x21)
      out.u32(crc)
      out.u32(data.size.toLong())
      out.u32(data.size.toLong())
      out.u16(nameBytes.size)
      out.u16(0)
      out.write(nameBytes)
      out.write(data)

      central.u32(0x02014b50)
      central.u16(0x031e)
      central.u16(10)
      central.u16(0x0800)
      central.u16(0)
      central.u16(0)
      central.u16(0x21)
      central.u32(crc)
      central.u32(data.size.toLong())
      central.u32(data.size.toLong())
      central.u16(nameBytes.size)
      central.u16(0)
      central.u16(0)
      central.u16(0)
      central.u16(0)
      central.u32(if (content == null) 0x41ed0010L else 0x81a40000L)
      central.u32(offset)
      central.write(nameBytes)
    }
    val cdOffset = out.size().toLong()
    val cd = central.toByteArray()
    out.write(cd)
    out.u32(0x06054b50)
    out.u16(0)
    out.u16(0)
    out.u16(entries.size)
    out.u16(entries.size)
    out.u32(cd.size.toLong())
    out.u32(cdOffset)
    out.u16(0)
    return out.toByteArray()
  }

  fun write(
    path: Path,
    entries: List<Pair<String, ByteArray?>>,
  ): Path = path.also { it.writeBytes(bytes(entries)) }

  /** text entry */
  fun t(
    name: String,
    content: String,
  ): Pair<String, ByteArray?> = name to content.toByteArray(Charsets.UTF_8)
}
