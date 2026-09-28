package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class ThumbnailBookDtoOracleTest : OracleTest() {
  private val t =
    ThumbnailBook(
      thumbnail = byteArrayOf(1, 2, 3),
      selected = true,
      type = ThumbnailBook.Type.GENERATED,
      mediaType = "image/jpeg",
      fileSize = 12345,
      dimension = Dimension(300, 400),
      id = "T1",
      bookId = "X1",
      createdDate = LocalDateTime.of(2020, 1, 1, 0, 0),
    )

  override fun cases() {
    func("toDto") {
      case("GENERATED") { t.toDto() }
      case("SIDECAR, not selected") { t.copy(type = ThumbnailBook.Type.SIDECAR, selected = false, dimension = Dimension(0, 0)).toDto() }
      case("json") { json(t.toDto()) }
    }
  }
}
