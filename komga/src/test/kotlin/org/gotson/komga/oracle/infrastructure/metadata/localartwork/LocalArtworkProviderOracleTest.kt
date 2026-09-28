package org.gotson.komga.oracle.infrastructure.metadata.localartwork

import org.apache.tika.config.TikaConfig
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.infrastructure.image.ImageAnalyzer
import org.gotson.komga.infrastructure.mediacontainer.ContentDetector
import org.gotson.komga.infrastructure.metadata.localartwork.LocalArtworkProvider
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.mediacontainer.Samples
import org.gotson.komga.oracle.infrastructure.metadata.MetadataSamples
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.toPath
import kotlin.io.path.writeBytes

class LocalArtworkProviderOracleTest : OracleTest() {
  private val provider = LocalArtworkProvider(ContentDetector(TikaConfig()), ImageAnalyzer())

  private val png = Samples.komgaRes("barcode/komga.png").readBytes()
  private val jpg = Samples.komgaRes("hashpage/e-sou.jpeg/1.jpg").readBytes()
  private val webp = Samples.komgaRes("hashpage/e-sou.webp/1.webp").readBytes()
  private val gif = Samples.komgaRes("hashpage/tr.gif/1.gif").readBytes()

  private fun files(
    dir: Path,
    files: List<Pair<String, ByteArray?>>,
  ): Path {
    dir.createDirectories()
    for ((name, content) in files) {
      if (content == null) dir.resolve(name).createDirectories() else dir.resolve(name).writeBytes(content)
    }
    return dir
  }

  /** Directory order is not specified: thumbnails sorted by file name, and the number of selected ones */
  private fun books(thumbnails: List<ThumbnailBook>) =
    listOf(
      thumbnails.map { listOf(it.url!!.toURI().toPath().name, it.type, it.bookId, it.fileSize, it.mediaType, it.dimension) }.sortedBy { it[0] as String },
      thumbnails.count { it.selected },
    )

  private fun series(thumbnails: List<ThumbnailSeries>) =
    listOf(
      thumbnails.map { listOf(it.url!!.toURI().toPath().name, it.type, it.seriesId, it.fileSize, it.mediaType, it.dimension) }.sortedBy { it[0] as String },
      thumbnails.count { it.selected },
    )

  private val bookDirs: List<Triple<String, String, List<Pair<String, ByteArray?>>>> by lazy {
    listOf(
      Triple("no sidecar", "Book.cbz", listOf("Book.cbz" to png)),
      Triple(
        "all extensions",
        "Book.cbz",
        listOf(
          "Book.cbz" to png, "Book.png" to png, "Book.jpg" to jpg, "Book.jpeg" to jpg, "Book.webp" to webp, "Book.gif" to gif, "Book.tbn" to png,
          "Book.bmp" to png, "Book.txt" to png,
        ),
      ),
      Triple(
        "numbered and case",
        "Book.cbz",
        listOf("Book.cbz" to png, "book-1.PNG" to png, "BOOK-22.Jpg" to jpg, "Book-.png" to png, "Book-a.png" to png, "Book 1.png" to png, "Book-1-2.png" to png, "Other.png" to png),
      ),
      Triple("not images", "Book.cbz", listOf("Book.cbz" to png, "Book.png" to "text".toByteArray(), "Book.jpg" to ByteArray(0), "Book-1.png" to png.copyOf(60))),
      Triple("directory named like a sidecar", "Book.cbz", listOf("Book.cbz" to png, "Book.png" to null)),
      Triple("regex characters", "Book (1) [x].cbz", listOf("Book (1) [x].cbz" to png, "Book (1) [x].png" to png, "Book (1) [x]-2.png" to png, "Book 1 x.png" to png)),
      Triple("dots in name", "My.Book.v1.cbz", listOf("My.Book.v1.cbz" to png, "My.Book.v1.png" to png, "My.Book.png" to png, "My.Book.v1-1.jpg" to jpg)),
      Triple("unicode", "Été.cbz", listOf("Été.cbz" to png, "ÉTÉ.png" to png, "été-1.png" to png)),
    )
  }

  private val seriesDirs: List<Pair<String, List<Pair<String, ByteArray?>>>> by lazy {
    listOf(
      "empty" to emptyList(),
      "all names" to listOf("cover.jpg" to jpg, "default.png" to png, "folder.webp" to webp, "poster.gif" to gif, "series.tbn" to png, "other.jpg" to jpg),
      "case" to listOf("Cover.JPG" to jpg, "FOLDER.Png" to png, "Series.JPEG" to jpg),
      "not images" to listOf("cover.jpg" to "text".toByteArray(), "folder.png" to ByteArray(0), "poster.png" to png.copyOf(60)),
      "unsupported" to listOf("cover.bmp" to png, "cover-1.jpg" to jpg, "covers.jpg" to jpg, "cover" to png, "cover.jpg" to null),
    )
  }

  override fun cases() {
    func("getBookThumbnails") {
      for ((label, name, content) in bookDirs) {
        case(label) {
          val dir = files(tempDir.resolve("book-$label"), content)
          books(provider.getBookThumbnails(MetadataSamples.book(dir.resolve(name).toUri().toURL())))
        }
      }
    }
    func("getSeriesThumbnails") {
      for ((label, content) in seriesDirs) {
        for (oneshot in listOf(false, true)) {
          case("$label (oneshot $oneshot)") {
            val dir = files(tempDir.resolve("series-$label"), content)
            series(provider.getSeriesThumbnails(Series("series", dir.toUri().toURL(), MetadataSamples.date, id = "SERIES", oneshot = oneshot, createdDate = MetadataSamples.date)))
          }
        }
      }
    }
    func("getSidecarBookType") { case("type") { provider.getSidecarBookType() } }
    func("getSidecarSeriesType") { case("type") { provider.getSidecarSeriesType() } }
    func("getSidecarSeriesFilenames") { case("names") { provider.getSidecarSeriesFilenames() } }
    func("getSidecarBookPrefilter") {
      val names =
        listOf(
          "a.png", "a.PNG", "a-1.jpg", "a.jpeg", "a.tbn", "a.webp", "a.gif", "a.bmp", "a.png.txt", ".png", "png", "a-1-2.gif", "dir/a.jpg", "a\nb.png", "a.jpg ",
          "a-x.webp", "Ä.JPG",
        )
      case("matches") { provider.getSidecarBookPrefilter().map { regex -> names.map { regex.matches(it) } } }
    }
    func("isSidecarBookMatch") {
      val basenames = listOf("Book", "book", "Book (1) [x]", "a.b", "Bo*k", "", "Été", "Book-1", "\\Q")
      val sidecars =
        listOf(
          "Book.jpg", "book.JPG", "Book-1.png", "Book-12.jpg", "Book-.jpg", "Book-a.jpg", "Book 1.jpg", "Book (1) [x].jpg", "Book (1) [x]-3.webp", "a.b.jpg",
          "axb.jpg", "Bo*k.png", "Book.tar.gz", ".jpg", "Book", "dir/Book.jpg", "dir\\Book.jpg", "ÉTÉ.jpg", "été.jpg", "Book-1-2.jpg", "\\Q.png",
        )
      for (b in basenames) {
        for (s in sidecars) case("'$b', '$s'") { provider.isSidecarBookMatch(b, s) }
      }
    }
  }
}
