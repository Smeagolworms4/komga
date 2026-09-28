package org.gotson.komga.oracle.interfaces.api

import org.gotson.komga.domain.model.Media
import org.gotson.komga.interfaces.api.getBookLastModified
import org.gotson.komga.interfaces.api.setNotModified
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

class UtilsOracleTest : OracleTest() {
  private fun media(date: LocalDateTime) = Media(createdDate = date, lastModifiedDate = date)

  private val dates =
    listOf(
      LocalDateTime.of(2020, 1, 2, 3, 4, 5),
      LocalDateTime.of(2020, 1, 2, 3, 4, 5, 678_000_000),
      LocalDateTime.of(2020, 1, 2, 3, 4, 5, 999_999_999),
      LocalDateTime.of(1970, 1, 1, 0, 0),
      LocalDateTime.of(1969, 12, 31, 23, 59, 59, 500_000_000),
      LocalDateTime.of(2021, 3, 28, 2, 30),
      LocalDateTime.of(9999, 12, 31, 23, 59, 59),
    )

  override fun cases() {
    func("getBookLastModified") {
      dates.forEach { case("$it") { getBookLastModified(media(it)) } }
    }
    func("setNotModified") {
      dates.forEach { case("$it") { WebOracle.describeEntity(ResponseEntity.ok().setNotModified(media(it)).build<Any>()) } }
    }
  }
}
