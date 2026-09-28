package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Library
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.net.URL
import java.time.LocalDateTime

class LibraryDtoOracleTest : OracleTest() {
  private val lib = Library(name = "Lib", root = URL("file:/data/My%20Comics/"), id = "L1", createdDate = LocalDateTime.of(2020, 1, 1, 0, 0))
  private val full =
    lib.copy(
      scanInterval = Library.ScanInterval.EVERY_6H,
      seriesCover = Library.SeriesCover.FIRST_UNREAD_OR_LAST,
      scanDirectoryExclusions = setOf("b", "a", "#recycle"),
      oneshotsDirectory = "_oneshots",
      unavailableDate = LocalDateTime.of(2021, 1, 1, 0, 0),
      importBarcodeIsbn = false,
      hashKoreader = true,
    )

  override fun cases() {
    func("toDto") {
      case("defaults, include root") { lib.toDto(true) }
      case("defaults, exclude root") { lib.toDto(false) }
      case("all fields") { full.toDto(true) }
      case("unicode root") { lib.copy(root = URL("file:/data/%C3%A9t%C3%A9/")).toDto(true) }
      case("json") { json(full.toDto(true)) }
      case("json no root") { json(lib.toDto(false)) }
    }
  }
}
