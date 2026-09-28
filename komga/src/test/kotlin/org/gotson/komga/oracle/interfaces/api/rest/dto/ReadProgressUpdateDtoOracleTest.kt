package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.interfaces.api.rest.dto.ReadProgressUpdateDto
import org.gotson.komga.interfaces.api.rest.dto.ReadProgressUpdateDtoValidator
import org.gotson.komga.oracle.OracleTest

class ReadProgressUpdateDtoOracleTest : OracleTest() {
  private val v = ReadProgressUpdateDtoValidator()

  override fun cases() {
    func("isValid") {
      case("null") { v.isValid(null, null) }
      case("page only") { v.isValid(ReadProgressUpdateDto(5, null), null) }
      case("page 0") { v.isValid(ReadProgressUpdateDto(0, null), null) }
      case("completed true") { v.isValid(ReadProgressUpdateDto(null, true), null) }
      case("completed false") { v.isValid(ReadProgressUpdateDto(null, false), null) }
      case("nothing") { v.isValid(ReadProgressUpdateDto(null, null), null) }
      case("page and completed false") { v.isValid(ReadProgressUpdateDto(3, false), null) }
    }
  }
}
