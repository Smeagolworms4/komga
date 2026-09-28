package org.gotson.komga.oracle.interfaces.api.kobo.dto

import org.gotson.komga.interfaces.api.kobo.dto.ResultDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle

class ResultDtoOracleTest : OracleTest() {
  override fun cases() {
    func("wrapped") {
      ResultDto.entries.forEach { r -> case(r.name) { WebOracle.mapper.writeValueAsString(r.wrapped()) } }
    }
  }
}
