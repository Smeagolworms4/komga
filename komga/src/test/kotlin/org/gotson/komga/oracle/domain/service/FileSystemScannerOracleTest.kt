package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.ScanResult
import org.gotson.komga.domain.service.getUpdatedTime
import org.gotson.komga.domain.service.toLocalDateTime
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.attempt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class FileSystemScannerOracleTest : OracleTest() {
  private val graph = ServiceGraph(OracleDb())
  private val scanner = graph.fileSystemScanner

  private val root by lazy { tempDir.resolve("root").createDirectories() }

  /** Fixed modification time in the future (the creation time cannot be set, getUpdatedTime takes the max) */
  private val t1 = FileTime.from(Instant.parse("2030-01-02T03:04:05Z"))
  private val t2 = FileTime.from(Instant.parse("2031-06-07T08:09:10Z"))

  private fun file(
    rel: String,
    size: Int = 10,
    time: FileTime = t1,
  ) {
    val p = root.resolve(rel)
    p.parent.createDirectories()
    p.writeBytes(oracleBytes(size))
    Files.setLastModifiedTime(p, time)
  }

  /** Sets the modification time of every directory (after their content is written) */
  private fun touchDirs() {
    Files.walk(root).use { s -> s.filter { Files.isDirectory(it) && !Files.isSymbolicLink(it) }.toList() }.sortedDescending().forEach { Files.setLastModifiedTime(it, t1) }
  }

  private fun book(b: Book) = listOf(b.name, b.url, b.fileLastModified, b.fileSize, b.oneshot)

  private fun describe(r: ScanResult) =
    listOf(
      r.series.entries
        .map { (s, books) -> listOf(s.name, s.url, s.fileLastModified, s.oneshot, books.map { book(it) }.sortedBy { it[1].toString() }) }
        .sortedBy { it[1].toString() + it[0] },
      r.sidecars.map { listOf(it.url, it.parentUrl, it.lastModifiedTime, it.type, it.source) }.sortedBy { it[0].toString() + it[1] },
    )

  private fun scan(block: () -> Any?) = attempt(root, block)

  override fun cases() {
    func("scanRootFolder") {
      case("setup") {
        file("a/one.cbz")
        file("a/two.CBR", 20)
        file("a/three.pdf", 30, t2)
        file("a/four.epub")
        file("a/five.zip")
        file("a/six.rar")
        file("a/notes.txt")
        file("a/.hidden.cbz")
        file("a/cover.jpg")
        file("a/one.png")
        file("a/one-2.jpg")
        file("a/two-1.webp")
        file("a/series.json")
        file("a/folder.PNG")
        file("b/sub/deep.cbz")
        file("b/series.json")
        file("b/poster.jpg")
        file(".hiddendir/x.cbz")
        file("@eaDir/y.cbz")
        file("Recycle/z.cbz")
        file("_oneshots/os1.cbz")
        file("_oneshots/os1.jpg")
        file("_oneshots/sub/os2.cbz")
        file("empty/readme.txt")
        file("ünï cödé/漫画 1.cbz")
        root.resolve("links").createDirectories()
        Files.createSymbolicLink(root.resolve("links/linked.cbz"), root.resolve("a/one.cbz"))
        Files.createSymbolicLink(root.resolve("links/broken.cbz"), root.resolve("nope.cbz"))
        Files.createSymbolicLink(root.resolve("linkdir"), root.resolve("b/sub"))
        file("root.cbz")
        touchDirs()
        true
      }
      case("defaults") { scan { describe(scanner.scanRootFolder(root)) } }
      case("force directory modified time") { scan { describe(scanner.scanRootFolder(root, forceDirectoryModifiedTime = true)) } }
      case("oneshots directory") { scan { describe(scanner.scanRootFolder(root, oneshotsDir = "_ONESHOTS")) } }
      case("blank oneshots directory") { scan { describe(scanner.scanRootFolder(root, oneshotsDir = " ")) } }
      case("cbx only") { scan { describe(scanner.scanRootFolder(root, scanPdf = false, scanEpub = false)) } }
      case("nothing scanned") { scan { describe(scanner.scanRootFolder(root, scanCbx = false, scanPdf = false, scanEpub = false)) } }
      case("directory exclusions") { scan { describe(scanner.scanRootFolder(root, directoryExclusions = setOf("@eaDir", "recycle", "SUB"))) } }
      case("exclusion matching root") { scan { describe(scanner.scanRootFolder(root, directoryExclusions = setOf("root"))) } }
      case("subfolder") { scan { describe(scanner.scanRootFolder(root.resolve("b"))) } }
      case("missing folder") { scan { scanner.scanRootFolder(root.resolve("nope")) } }
      case("file as root") { scan { scanner.scanRootFolder(root.resolve("root.cbz")) } }
      case("series name of root") { scan { describe(scanner.scanRootFolder(root.resolve("a"))) } }
    }
    func("preVisitDirectory") {
      case("hidden and excluded directories skipped") { scan { describe(scanner.scanRootFolder(root, directoryExclusions = setOf("ünï"))).let { (s, _) -> s } } }
    }
    func("visitFile") {
      case("extensions, hidden files and sidecars") { scan { describe(scanner.scanRootFolder(root.resolve("a"))) } }
      case("symbolic links") { scan { describe(scanner.scanRootFolder(root.resolve("links"))) } }
    }
    func("postVisitDirectory") {
      case("series with books only") { scan { describe(scanner.scanRootFolder(root.resolve("empty"))) } }
      case("oneshots sidecars") { scan { describe(scanner.scanRootFolder(root.resolve("_oneshots"), oneshotsDir = "oneshots")) } }
    }
    func("visitFileFailed") {
      case("unreadable directory") {
        val locked = root.resolve("locked").createDirectories()
        locked.resolve("l.cbz").writeBytes(oracleBytes(1))
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))
        try {
          scan { describe(scanner.scanRootFolder(root, directoryExclusions = setOf("a", "b", "_", "ü", "links", "empty", "Recycle", "@"))) }
        } finally {
          Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
      }
    }
    func("scanFile") {
      case("book") { scan { scanner.scanFile(root.resolve("a/three.pdf"))?.let { book(it) } } }
      case("any extension") { scan { scanner.scanFile(root.resolve("a/notes.txt"))?.let { book(it) } } }
      case("missing") { scanner.scanFile(root.resolve("nope.cbz")) }
      case("directory") { scan { scanner.scanFile(root.resolve("a"))?.let { book(it) } } }
    }
    func("scanBookSidecars") {
      case("book with sidecars") { scan { scanner.scanBookSidecars(root.resolve("a/one.cbz")).map { listOf(it.url, it.parentUrl, it.lastModifiedTime, it.type, it.source) }.sortedBy { it[0].toString() } } }
      case("book without sidecar") { scan { scanner.scanBookSidecars(root.resolve("a/three.pdf")) } }
      case("missing book in existing folder") { scan { scanner.scanBookSidecars(root.resolve("a/two.cbz")).map { it.url } } }
      case("missing folder") { scan { scanner.scanBookSidecars(root.resolve("nope/x.cbz")) } }
    }
    func("pathToBook") {
      case("unicode name") { scan { scanner.scanFile(root.resolve("ünï cödé/漫画 1.cbz"))?.let { book(it) } } }
    }
    func("getUpdatedTime") {
      case("modified time in the future") { Files.readAttributes(root.resolve("a/three.pdf"), BasicFileAttributes::class.java).getUpdatedTime() }
    }
    func("toLocalDateTime") {
      for (s in listOf("2030-01-02T03:04:05Z", "1970-01-01T00:00:00Z", "2021-03-28T01:30:00Z", "2021-10-31T01:30:00.123456789Z")) {
        case(s) { FileTime.from(Instant.parse(s)).toLocalDateTime() }
      }
      case("epoch millis") { FileTime.fromMillis(1234567890123).toLocalDateTime() }
    }
  }

}
