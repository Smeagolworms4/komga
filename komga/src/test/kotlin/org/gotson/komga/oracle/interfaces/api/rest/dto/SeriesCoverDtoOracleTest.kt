package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Library
import org.gotson.komga.interfaces.api.rest.dto.SeriesCoverDto
import org.gotson.komga.interfaces.api.rest.dto.toDomain
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest

class SeriesCoverDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      Library.SeriesCover.entries.forEach { case(it.name) { it.toDto() } }
    }
    func("toDomain") {
      SeriesCoverDto.entries.forEach { case(it.name) { it.toDomain() } }
    }
  }
}
