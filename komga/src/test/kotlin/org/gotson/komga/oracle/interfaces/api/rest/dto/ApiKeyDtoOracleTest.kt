package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.ApiKey
import org.gotson.komga.interfaces.api.rest.dto.redacted
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class ApiKeyDtoOracleTest : OracleTest() {
  private val key =
    ApiKey(
      id = "K1",
      userId = "U1",
      key = "secretkey",
      comment = "my key",
      createdDate = LocalDateTime.of(2021, 6, 15, 12, 30, 45, 123000000),
      lastModifiedDate = LocalDateTime.of(2022, 1, 1, 0, 0),
    )

  override fun cases() {
    func("toDto") {
      case("summer, lastModified is createdDate") { key.toDto() }
      case("winter") { key.copy(createdDate = LocalDateTime.of(2021, 1, 15, 0, 0)).toDto() }
      case("dst gap") { key.copy(createdDate = LocalDateTime.of(2021, 3, 28, 2, 30)).toDto() }
      case("dst overlap") { key.copy(createdDate = LocalDateTime.of(2021, 10, 31, 2, 30)).toDto() }
      case("json") { json(key.toDto()) }
    }
    func("redacted") {
      case("key replaced") { key.toDto().redacted() }
      case("empty key") { key.copy(key = "").toDto().redacted() }
      case("json") { json(key.toDto().redacted()) }
    }
  }
}
