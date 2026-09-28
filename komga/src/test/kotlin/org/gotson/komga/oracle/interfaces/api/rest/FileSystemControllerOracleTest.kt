package org.gotson.komga.oracle.interfaces.api.rest

import org.gotson.komga.interfaces.api.rest.DirectoryListingDto
import org.gotson.komga.interfaces.api.rest.DirectoryRequestDto
import org.gotson.komga.interfaces.api.rest.FileSystemController
import org.gotson.komga.interfaces.api.rest.PathDto
import org.gotson.komga.interfaces.api.rest.toDto
import org.gotson.komga.oracle.OracleTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class FileSystemControllerOracleTest : OracleTest() {
  private val controller = FileSystemController()
  private val root: Path by lazy { tempDir.resolve("fs").also { setup(it) } }

  /** Same tree in the TypeScript twin */
  private fun setup(r: Path) {
    listOf("Beta", "alpha", "Gamma dir", ".hidden", "empty", "Delta/sub").forEach { r.resolve(it).createDirectories() }
    listOf("b.cbz", "A.cbz", "c.txt", ".dot", "alpha/inner.pdf").forEach { r.resolve(it).writeBytes(oracleBytes(3)) }
  }

  private fun rel(s: String?) = s?.replace(root.parent.toString(), "<tmp>")

  private fun rel(p: PathDto) = listOf(p.type, rel(p.name), rel(p.path))

  private fun rel(d: DirectoryListingDto) = listOf(rel(d.parent), d.directories.map { rel(it) }, d.files.map { rel(it) })

  private fun list(
    path: String,
    showFiles: Boolean = false,
  ) = rel(controller.getDirectoryListing(DirectoryRequestDto(path.replace("<tmp>", root.parent.toString()), showFiles)))

  override fun cases() {
    func("getDirectoryListing") {
      case("no path: roots") { controller.getDirectoryListing(DirectoryRequestDto()) }
      case("default request") { controller.getDirectoryListing() }
      case("relative path") { controller.getDirectoryListing(DirectoryRequestDto("relative/dir")) }
      case("directories only") { list("<tmp>/fs") }
      case("with files") { list("<tmp>/fs", true) }
      case("trailing slash") { list("<tmp>/fs/", true) }
      case("file path lists its directory") { list("<tmp>/fs/b.cbz", true) }
      case("missing file in existing directory") { list("<tmp>/fs/missing.cbz") }
      case("missing directory") { exceptionType { list("<tmp>/fs/nope/deeper") } }
      case("missing directory message") {
        try {
          list("<tmp>/fs/nope/deeper")
        } catch (e: Exception) {
          e.message
        }
      }
      case("empty directory") { list("<tmp>/fs/empty", true) }
      case("dot segments") { list("<tmp>/fs/alpha/../Beta", true) }
      case("hidden directory itself") { list("<tmp>/fs/.hidden", true) }
    }
    func("toDto") {
      case("directory") { rel(root.resolve("Beta").toDto()) }
      case("file") { rel(root.resolve("b.cbz").toDto()) }
      case("missing") { rel(root.resolve("nothing").toDto()) }
      case("root") { Path.of("/").toDto() }
      case("relative") { Path.of("some/rel").toDto() }
      case("exists check") { Files.exists(root) }
    }
  }
}
