package org.gotson.komga.oracle.interfaces.api.rest.dto

import org.gotson.komga.domain.model.AuthenticationActivity
import org.gotson.komga.interfaces.api.rest.dto.toDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.api.rest.RestOracle.json
import java.time.LocalDateTime

class AuthenticationActivityDtoOracleTest : OracleTest() {
  private val full =
    AuthenticationActivity(
      userId = "U1",
      email = "a@b.c",
      apiKeyId = "K1",
      apiKeyComment = "comment",
      ip = "127.0.0.1",
      userAgent = "agent",
      success = false,
      error = "Bad credentials",
      dateTime = LocalDateTime.of(2021, 7, 1, 10, 20, 30, 999999999),
      source = "Password",
    )

  override fun cases() {
    func("toDto") {
      case("all fields") { full.toDto() }
      case("nulls") { AuthenticationActivity(success = true, dateTime = LocalDateTime.of(2020, 12, 31, 23, 59, 59)).toDto() }
      case("dst gap") { full.copy(dateTime = LocalDateTime.of(2021, 3, 28, 2, 0)).toDto() }
      case("json all fields") { json(full.toDto()) }
      case("json nulls") { json(AuthenticationActivity(success = true, dateTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0, 500)).toDto()) }
    }
  }
}
