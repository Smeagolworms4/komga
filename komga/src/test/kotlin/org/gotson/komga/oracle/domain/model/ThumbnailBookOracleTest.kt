package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class ThumbnailBookOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun thumb(
    thumbnail: ByteArray? = byteArrayOf(1, 2, 3),
    url: URL? = null,
    selected: Boolean = false,
    mediaType: String = "image/jpeg",
    fileSize: Long = 3,
    dimension: Dimension = Dimension(10, 20),
    id: String = "T1",
    ownerId: String = "O1",
    createdDate: LocalDateTime = date,
    lastModifiedDate: LocalDateTime = createdDate,
  ) = ThumbnailBook(thumbnail, url, selected, ThumbnailBook.Type.GENERATED, mediaType, fileSize, dimension, id, ownerId, createdDate, lastModifiedDate)

  override fun cases() {
    func("exists") {
      case("bytes") { thumb().exists() }
      case("empty bytes") { thumb(thumbnail = byteArrayOf()).exists() }
      case("no bytes, no url") { thumb(thumbnail = null).exists() }
      case("existing file url") {
        val f = tempDir.resolve("thumb one.jpg").toFile().apply { writeBytes(oracleBytes(4)) }
        thumb(thumbnail = null, url = f.toURI().toURL()).exists()
      }
      case("missing file url") { thumb(url = tempDir.resolve("missing.jpg").toUri().toURL()).exists() }
      case("existing directory url") { thumb(url = tempDir.toUri().toURL()).exists() }
      case("url wins over bytes") { thumb(url = tempDir.resolve("nope.png").toUri().toURL()).exists() }
      case("http url") { exceptionType { thumb(url = URL("http://example.org/a.jpg")).exists() } }
    }
    func("equals") {
      case("same values, distinct arrays") { thumb() == thumb(thumbnail = byteArrayOf(1, 2, 3)) }
      case("same instance") { thumb().let { it == it } }
      case("different bytes") { thumb() == thumb(thumbnail = byteArrayOf(1, 2, 4)) }
      case("empty bytes") { thumb(thumbnail = byteArrayOf()) == thumb(thumbnail = byteArrayOf()) }
      case("prefix bytes") { thumb() == thumb(thumbnail = byteArrayOf(1, 2)) }
      case("both null bytes") { thumb(thumbnail = null) == thumb(thumbnail = null) }
      case("null vs bytes") { thumb(thumbnail = null) == thumb() }
      case("bytes vs null") { thumb() == thumb(thumbnail = null) }
      case("same url") { thumb(url = URL("file:/a.jpg")) == thumb(url = URL("file:/a.jpg")) }
      case("different url") { thumb(url = URL("file:/a.jpg")) == thumb(url = URL("file:/b.jpg")) }
      case("url vs null") { thumb(url = URL("file:/a.jpg")) == thumb() }
      case("selected") { thumb() == thumb(selected = true) }
      case("media type") { thumb() == thumb(mediaType = "image/png") }
      case("file size") { thumb() == thumb(fileSize = 4) }
      case("dimension") { thumb() == thumb(dimension = Dimension(20, 10)) }
      case("id") { thumb() == thumb(id = "T2") }
      case("owner") { thumb() == thumb(ownerId = "O2") }
      case("created date") { thumb() == thumb(createdDate = date.plusNanos(1), lastModifiedDate = date) }
      case("last modified date") { thumb() == thumb(lastModifiedDate = date.plusDays(1)) }
      case("null") { thumb().equals(null) }
      case("other type") { thumb().equals("T1") }
    }
    func("hashCode") {
      case("equal values have equal hash") { thumb().hashCode() == thumb(thumbnail = byteArrayOf(1, 2, 3)).hashCode() }
      case("stable") { thumb().let { it.hashCode() == it.hashCode() } }
      case("different bytes change hash") { thumb().hashCode() == thumb(thumbnail = byteArrayOf(3, 2, 1)).hashCode() }
      case("null bytes hash equal") { thumb(thumbnail = null).hashCode() == thumb(thumbnail = null).hashCode() }
      case("equal urls hash equal") { thumb(url = URL("file:/a.jpg")).hashCode() == thumb(url = URL("file:/a.jpg")).hashCode() }
      case("different id change hash") { thumb().hashCode() == thumb(id = "T2").hashCode() }
    }
  }
}
