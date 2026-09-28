package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailReadList
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class ThumbnailReadListOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun thumb(
    thumbnail: ByteArray = byteArrayOf(1, 2, 3),
    selected: Boolean = false,
    mediaType: String = "image/jpeg",
    fileSize: Long = 3,
    dimension: Dimension = Dimension(10, 20),
    id: String = "T1",
    ownerId: String = "O1",
    createdDate: LocalDateTime = date,
    lastModifiedDate: LocalDateTime = createdDate,
  ) = ThumbnailReadList(thumbnail, selected, ThumbnailReadList.Type.USER_UPLOADED, mediaType, fileSize, dimension, id, ownerId, createdDate, lastModifiedDate)

  override fun cases() {
    func("equals") {
      case("same values, distinct arrays") { thumb() == thumb(thumbnail = byteArrayOf(1, 2, 3)) }
      case("same instance") { thumb().let { it == it } }
      case("different bytes") { thumb() == thumb(thumbnail = byteArrayOf(1, 2, 4)) }
      case("empty bytes") { thumb(thumbnail = byteArrayOf()) == thumb(thumbnail = byteArrayOf()) }
      case("prefix bytes") { thumb() == thumb(thumbnail = byteArrayOf(1, 2)) }
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
      case("different id change hash") { thumb().hashCode() == thumb(id = "T2").hashCode() }
    }
  }
}
