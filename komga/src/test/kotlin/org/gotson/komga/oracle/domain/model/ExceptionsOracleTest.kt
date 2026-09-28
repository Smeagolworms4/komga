package org.gotson.komga.oracle.domain.model

import org.gotson.komga.domain.model.CodedException
import org.gotson.komga.domain.model.MediaUnsupportedException
import org.gotson.komga.domain.model.withCode
import org.gotson.komga.oracle.OracleTest
import java.io.IOException

class ExceptionsOracleTest : OracleTest() {
  private fun describe(e: CodedException) = listOf(e::class.simpleName, e.message, e.code, e.cause?.let { it::class.simpleName }, e.cause?.message)

  override fun cases() {
    func("withCode") {
      case("illegal state") { describe(IllegalStateException("boom").withCode("ERR_1")) }
      case("no message") { describe(IllegalArgumentException().withCode("ERR_2")) }
      case("empty code") { describe(IOException("io").withCode("")) }
      case("coded exception") { describe(MediaUnsupportedException("unsupported", "ERR_3").withCode("ERR_4")) }
      case("coded exception, default code") { describe(MediaUnsupportedException("unsupported").withCode("X")) }
      case("unicode message") { describe(Exception("échec 漫画").withCode("ERR_5")) }
      case("thrown") { throw RuntimeException("inner").withCode("ERR_6") }
      case("cause kept") { RuntimeException("inner").let { it.withCode("C").cause === it } }
    }
  }
}
