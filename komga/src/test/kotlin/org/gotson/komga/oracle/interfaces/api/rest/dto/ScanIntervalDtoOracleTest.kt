package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.Library
import org.gotson.komga.interfaces.api.rest.dto.ScanIntervalDto
import org.gotson.komga.interfaces.api.rest.dto.toDomain
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest

class ScanIntervalDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      Library.ScanInterval.entries.forEach { case(it.name) { it.toDto() } }
    }
    func("toDomain") {
      ScanIntervalDto.entries.forEach { case(it.name) { it.toDomain() } }
    }
  }
}
