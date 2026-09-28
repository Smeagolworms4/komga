package org.gotson.komga.oracle.infrastructure.mediacontainer

import org.gotson.komga.oracle.infrastructure.mediacontainer.OracleZip.t
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes

/** Resources of the oracle tests, mirrored by test/unit/infrastructure/mediacontainer/samples.ts in KomgaJS */
object Samples {
  /** Komga test resources (test/resources in KomgaJS) */
  fun komgaRes(p: String): Path = Path("src/test/resources/$p")

  /** Media container fixtures (test/infrastructure/mediacontainer/fixtures in KomgaJS) */
  fun fixture(p: String): Path = Path("src/test/resources/oracle/mediacontainer/$p")

  /** CRC-32 and size of extracted bytes (the bytes themselves would bloat the fixtures) */
  fun digest(b: ByteArray): List<Long> = listOf(b.size.toLong(), java.util.zip.CRC32().also { it.update(b) }.value)

  /** Result of [block], or its exception with [path] removed from the message (machine-dependent) */
  fun pathless(
    path: Path,
    block: () -> Any?,
  ): Any? =
    try {
      block()
    } catch (e: Throwable) {
      listOf("throws", e::class.java.simpleName, e.message?.replace(path.toString(), "<path>"))
    }

  /** Synthetic EPUBs (synthetic-epubs.json, same file in KomgaJS): name to ZIP entries */
  val syntheticEpubs: List<Pair<String, List<Pair<String, ByteArray?>>>> by lazy {
    val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(fixture("synthetic-epubs.json").toFile())
    json.map { epub ->
      epub[0].asText() to
        epub[1].map { entry ->
          val v = entry[1]
          entry[0].asText() to
            when {
              v.isNull -> null
              v.isTextual -> v.asText().toByteArray(Charsets.UTF_8)
              v.has("resource") -> komgaRes(v["resource"].asText()).readBytes()
              else -> java.util.Base64.getDecoder().decode(v["base64"].asText())
            }
        }
    }
  }

  /** Writes the synthetic EPUB [name] in [dir] */
  fun writeEpub(
    dir: Path,
    name: String,
  ): Path = OracleZip.write(dir.resolve("$name.epub"), syntheticEpubs.first { it.first == name }.second)

  val fixtureEpubs =
    listOf(
      "bad-mimetype.epub", "divina.epub", "divina-short.epub", "divina-text.epub", "epub3.epub", "kepub.epub", "mimetype-ws.epub", "missing-opf.epub",
      "no-rootfile.epub", "prefixed.epub", "reflow.epub", "spine-images.epub", "The Incomplete Theft - Ralph Burke.epub", "zip-as-epub.epub",
    )

  /** Every EPUB of the oracle tests (fixtures, then synthetic ones written in [dir]), with a label */
  fun epubFiles(dir: Path): List<Pair<String, Path>> =
    fixtureEpubs.map { "fixture $it" to fixture("epub/$it") } +
      listOf("komga zip.zip" to komgaRes("archives/zip.zip")) +
      syntheticEpubs.map { "synthetic ${it.first}" to writeEpub(dir, it.first) }

  private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  fun contents(oracleBytes: (Int) -> ByteArray): List<Pair<String, ByteArray>> =
    listOf(
      "empty" to ByteArray(0),
      "text" to "hello world\n".toByteArray(),
      "xml" to "<?xml version=\"1.0\"?><root/>".toByteArray(),
      "html" to "<html><body>hi</body></html>".toByteArray(),
      "xhtml" to "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body/></html>".toByteArray(),
      "pdf" to ascii("%PDF-1.4\n%âãÏÓ\n"),
      "zip" to OracleZip.bytes(listOf(t("a.txt", "a"))),
      "zip-empty" to OracleZip.bytes(emptyList()),
      "epub" to OracleZip.bytes(listOf(t("mimetype", "application/epub+zip"), t("META-INF/container.xml", "<container/>"))),
      "rar4" to ascii("Rar!\u001a\u0007\u0000") + oracleBytes(20),
      "rar5" to ascii("Rar!\u001a\u0007\u0001\u0000") + oracleBytes(20),
      "7z" to bytes(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c, 0, 4) + ByteArray(24),
      "png" to komgaRes("barcode/komga.png").readBytes(),
      "jpeg" to komgaRes("hashpage/e-sou.jpeg/1.jpg").readBytes(),
      "gif" to komgaRes("hashpage/tr.gif/1.gif").readBytes(),
      "webp" to komgaRes("hashpage/e-sou.webp/1.webp").readBytes(),
      "bmp" to ascii("BM") + ByteArray(50),
      "tiff" to ascii("II*\u0000") + ByteArray(20),
      "jxl" to bytes(0xff, 0x0a) + ByteArray(20),
      "avif" to bytes(0, 0, 0, 0x1c) + ascii("ftypavif\u0000\u0000\u0000\u0000avifmif1miaf"),
      "heic" to bytes(0, 0, 0, 0x18) + ascii("ftypheic\u0000\u0000\u0000\u0000mif1heic"),
      "random" to oracleBytes(512),
    )

  val names =
    listOf("noext", "file.cbz", "file.zip", "file.cbr", "file.rar", "file.epub", "file.kepub.epub", "file.pdf", "file.jpg", "file.png", "file.txt", "file.CBZ", "file.cb7", "file.xml")
}
