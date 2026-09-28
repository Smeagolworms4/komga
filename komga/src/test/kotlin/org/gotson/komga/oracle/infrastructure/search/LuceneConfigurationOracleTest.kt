package org.gotson.komga.oracle.infrastructure.search

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.TermQuery
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.infrastructure.search.LuceneConfiguration
import org.gotson.komga.infrastructure.search.toDocument
import org.gotson.komga.oracle.OracleTest
import kotlin.io.path.createDirectories

class LuceneConfigurationOracleTest : OracleTest() {
  private fun configuration(block: KomgaProperties.() -> Unit = {}) = LuceneConfiguration(KomgaProperties().apply(block))

  private val samples = listOf("Batman: Year One", "進撃の巨人", "Ｆｕｌｌｗｉｄｔｈ", "L'Été", "a", "ab", "abcdefghijklmnop")

  override fun cases() {
    func("indexAnalyzer") {
      for (t in samples) case("default settings '$t'") { SearchSamples.tokens(configuration().indexAnalyzer(), t) }
      for (t in samples) {
        case("1-2 without original '$t'") {
          SearchSamples.tokens(
            configuration {
              lucene.indexAnalyzer.minGram = 1
              lucene.indexAnalyzer.maxGram = 2
              lucene.indexAnalyzer.preserveOriginal = false
            }.indexAnalyzer(),
            t,
          )
        }
      }
    }
    func("searchAnalyzer") {
      for (t in samples) case("'$t'") { SearchSamples.tokens(configuration().searchAnalyzer(), t) }
    }
    func("memoryDirectory") {
      case("new directory has no index") { DirectoryReader.indexExists(configuration().memoryDirectory()) }
      case("two directories are distinct") { configuration().let { it.memoryDirectory() !== it.memoryDirectory() } }
    }
    func("diskDirectory") {
      case("new directory has no index") {
        val dir = tempDir.resolve("lucene-empty").createDirectories()
        DirectoryReader.indexExists(configuration { lucene.dataDirectory = dir.toString() }.diskDirectory())
      }
      case("index written then reopened") {
        val dir = tempDir.resolve("lucene-disk").createDirectories()
        val conf = configuration { lucene.dataDirectory = dir.toString() }
        val directory = conf.diskDirectory()
        val writer = conf.indexWriter(directory, conf.indexAnalyzer())
        writer.addDocuments(SearchSamples.books.map { it.toDocument() })
        writer.commit()
        writer.close()
        directory.close()
        val index = SearchSamples.Index(conf.diskDirectory())
        listOf(index.helper.indexExists(), index.searchAll())
      }
    }
    func("indexWriter") {
      case("documents visible after commit") {
        val conf = configuration()
        val directory = conf.memoryDirectory()
        val writer = conf.indexWriter(directory, conf.indexAnalyzer())
        val before = DirectoryReader.indexExists(directory)
        writer.addDocument(SearchSamples.collections[0].toDocument())
        writer.commit()
        val manager = conf.searcherManager(writer)
        val searcher = manager.acquire()
        listOf(before, DirectoryReader.indexExists(directory), searcher.search(TermQuery(Term("type", "collection")), 100).scoreDocs.size)
      }
    }
    func("searcherManager") {
      case("refresh after commit") {
        val conf = configuration()
        val writer = conf.indexWriter(conf.memoryDirectory(), conf.indexAnalyzer())
        val manager = conf.searcherManager(writer)
        val query = TermQuery(Term("type", "readlist"))
        val counts = mutableListOf(manager.acquire().search(query, 100).scoreDocs.size)
        writer.addDocuments(SearchSamples.readLists.map { it.toDocument() })
        counts.add(manager.acquire().search(query, 100).scoreDocs.size)
        writer.commit()
        counts.add(manager.acquire().search(query, 100).scoreDocs.size)
        manager.maybeRefreshBlocking()
        counts.add(manager.acquire().search(query, 100).scoreDocs.size)
        counts
      }
    }
  }
}
