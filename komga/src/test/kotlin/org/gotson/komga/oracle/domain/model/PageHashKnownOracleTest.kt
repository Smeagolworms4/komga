package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.PageHashKnown
import org.gotson.komga.oracle.OracleTest
import java.time.LocalDateTime

class PageHashKnownOracleTest : OracleTest() {
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val known = PageHashKnown("abc", 100, PageHashKnown.Action.DELETE_AUTO, 2, 3, date, date.plusDays(1))

  override fun cases() {
    func("copy") {
      case("no change") { stable(known.copy()) }
      case("hash") { stable(known.copy(hash = "def")) }
      case("size null") { stable(known.copy(size = null)) }
      case("negative size") { stable(known.copy(size = -1)) }
      case("zero size") { stable(known.copy(size = 0)) }
      case("action") { stable(known.copy(action = PageHashKnown.Action.IGNORE)) }
      case("counts") { stable(known.copy(deleteCount = -5, matchCount = Int.MAX_VALUE)) }
      case("dates reset") { known.copy().let { listOf(it.createdDate == date, it.createdDate == it.lastModifiedDate) } }
      case("class") { known.copy()::class.simpleName }
    }
  }
}
