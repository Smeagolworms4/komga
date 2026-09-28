package org.gotson.komga.oracle.infrastructure.search

import org.gotson.komga.infrastructure.search.MultiLingualNGramAnalyzer
import org.gotson.komga.oracle.OracleTest

class MultiLingualNGramAnalyzerOracleTest : OracleTest() {
  private val settings = listOf(Triple(3, 10, true), Triple(1, 2, false), Triple(2, 2, true), Triple(1, 1, false), Triple(4, 3, true), Triple(0, 2, false))

  override fun cases() {
    // through Analyzer.tokenStream
    func("createComponents") {
      for ((min, max, preserve) in settings) {
        val analyzer = MultiLingualNGramAnalyzer(min, max, preserve)
        for (t in SearchSamples.texts) case("$min-$max-$preserve '${t.take(40)}' (${t.length})") { SearchSamples.tokens(analyzer, t) }
      }
      case("normalize is inherited") { MultiLingualNGramAnalyzer(3, 10, true).normalize("title", "Ｂａｔｍａｎ Été").utf8ToString() }
    }
  }
}
