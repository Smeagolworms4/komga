package org.gotson.komga.oracle.interfaces.api.kobo.dto

import org.gotson.komga.interfaces.api.kobo.dto.toPeriodDto
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class PeriodDtoOracleTest : OracleTest() {
  override fun cases() {
    func("toPeriodDto") {
      case("utc") { WebOracle.mapper.writeValueAsString(ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC).toPeriodDto()) }
      case("nanos") { WebOracle.mapper.writeValueAsString(ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 123456789, ZoneOffset.UTC).toPeriodDto()) }
      case("offset") { WebOracle.mapper.writeValueAsString(ZonedDateTime.of(2020, 6, 2, 3, 4, 5, 0, ZoneOffset.ofHours(-5)).toPeriodDto()) }
      case("region") { WebOracle.mapper.writeValueAsString(ZonedDateTime.of(2020, 6, 2, 3, 4, 5, 0, ZoneId.of("Europe/Paris")).toPeriodDto()) }
    }
  }
}
