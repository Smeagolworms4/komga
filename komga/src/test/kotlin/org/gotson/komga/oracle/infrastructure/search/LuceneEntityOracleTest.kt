package org.gotson.komga.oracle.infrastructure.search

import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.oneshotDocument
import org.gotson.komga.infrastructure.search.toDocument
import org.gotson.komga.oracle.OracleTest

class LuceneEntityOracleTest : OracleTest() {
  override fun cases() {
    func("toDocument@30") {
      for (b in SearchSamples.books) case(b.id) { SearchSamples.fields(b.toDocument()) }
    }
    func("toDocument@50") {
      for (s in SearchSamples.series) case(s.id) { SearchSamples.fields(s.toDocument()) }
    }
    func("oneshotDocument") {
      val book = SearchSamples.books.first { it.oneshot }
      for (s in SearchSamples.series) case(s.id) { SearchSamples.fields(s.oneshotDocument(book.toDocument())) }
    }
    func("toDocument@105") {
      for (c in SearchSamples.collections) case(c.id) { SearchSamples.fields(c.toDocument()) }
    }
    func("toDocument@112") {
      for (r in SearchSamples.readLists) case(r.id) { SearchSamples.fields(r.toDocument()) }
    }
    func("entries") {
      case("properties") { LuceneEntity.entries.map { listOf(it, it.type, it.id, it.defaultFields.toList()) } }
      case("TYPE") { LuceneEntity.TYPE }
    }
  }
}
