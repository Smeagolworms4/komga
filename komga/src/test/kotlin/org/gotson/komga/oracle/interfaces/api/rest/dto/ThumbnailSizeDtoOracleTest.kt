package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.ThumbnailSize
import org.gotson.komga.interfaces.api.rest.dto.ThumbnailSizeDto
import org.gotson.komga.interfaces.api.rest.dto.toDomain
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest

class ThumbnailSizeDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toDto") {
      ThumbnailSize.entries.forEach { case(it.name) { it.toDto() } }
    }
    func("toDomain") {
      ThumbnailSizeDto.entries.forEach { case(it.name) { it.toDomain() } }
    }
  }
}
