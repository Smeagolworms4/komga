package org.gotson.komga.oracle.infrastructure.search

import org.gotson.komga.infrastructure.search.MultiLingualAnalyzer
import org.gotson.komga.oracle.OracleTest

class MultiLingualAnalyzerOracleTest : OracleTest() {
  private val analyzer = MultiLingualAnalyzer()

  override fun cases() {
    // through Analyzer.tokenStream
    func("createComponents") {
      for (t in SearchSamples.texts) case("'${t.take(40)}' (${t.length})") { SearchSamples.tokens(analyzer, t) }
      case("other field name") { SearchSamples.tokens(analyzer, "Batman Year One", "isbn") }
    }
    // through Analyzer.normalize(field, text)
    func("normalize") {
      for (t in SearchSamples.texts) case("'${t.take(40)}' (${t.length})") { analyzer.normalize("title", t).utf8ToString() }
    }
  }
}
