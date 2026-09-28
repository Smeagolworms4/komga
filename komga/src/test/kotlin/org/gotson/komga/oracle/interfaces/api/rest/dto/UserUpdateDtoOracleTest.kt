package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.AllowExcludeDto
import org.gotson.komga.interfaces.api.rest.dto.UserUpdateDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.read

class UserUpdateDtoOracleTest : OracleTest() {
  private fun state(json: String) =
    read<UserUpdateDto>(json).let { d ->
      listOf("ageRestriction", "labelsAllow", "labelsExclude", "roles", "sharedLibraries", "other").map { d.isSet(it) } +
        listOf(d.ageRestriction, d.labelsAllow, d.labelsExclude, d.roles, d.sharedLibraries)
    }

  override fun cases() {
    func("isSet") {
      case("empty body") { state("{}") }
      case("nulls") { state("""{"ageRestriction":null,"labelsAllow":null,"labelsExclude":null,"roles":null,"sharedLibraries":null}""") }
      case("values") {
        state("""{"ageRestriction":{"age":12,"restriction":"ALLOW_ONLY"},"labelsAllow":["a"],"labelsExclude":[],"roles":["ADMIN","FILE_DOWNLOAD"],"sharedLibraries":{"all":false,"libraryIds":["L2","L1"]}}""")
      }
    }
    func("toDomain") {
      case("ALLOW_ONLY") { AllowExcludeDto.ALLOW_ONLY.toDomain() }
      case("EXCLUDE") { AllowExcludeDto.EXCLUDE.toDomain() }
      case("NONE") { AllowExcludeDto.NONE.toDomain() }
    }
  }
}
