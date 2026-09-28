package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.LibraryUpdateDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read

class LibraryUpdateDtoOracleTest : OracleTest() {
  private fun state(json: String) =
    read<LibraryUpdateDto>(json).let { d ->
      listOf("name", "scanDirectoryExclusions", "oneshotsDirectory", "unknown").map { d.isSet(it) } +
        listOf(d.name, d.scanDirectoryExclusions, d.oneshotsDirectory, d.scanInterval, d.seriesCover)
    }

  override fun cases() {
    func("isSet") {
      case("empty body") { state("{}") }
      case("set to null") { state("""{"scanDirectoryExclusions":null,"oneshotsDirectory":null}""") }
      case("set to values") { state("""{"name":"n","scanDirectoryExclusions":["b","a"],"oneshotsDirectory":"","scanInterval":"WEEKLY","seriesCover":"LAST"}""") }
      case("only one") { state("""{"oneshotsDirectory":"os"}""") }
      case("default instance") { LibraryUpdateDto().isSet("scanDirectoryExclusions") }
    }
  }
}
