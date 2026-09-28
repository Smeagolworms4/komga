package org.gotson.komga.oracle.infrastructure.search

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.ByteBuffersDirectory
import org.gotson.komga.infrastructure.search.LuceneSyncCommitter
import org.gotson.komga.infrastructure.search.MultiLingualAnalyzer
import org.gotson.komga.infrastructure.search.toDocument
import org.gotson.komga.oracle.OracleTest

class LuceneSyncCommitterOracleTest : OracleTest() {
  override fun cases() {
    func("commitAndMaybeRefresh") {
      case("documents visible after commit") {
        val directory = ByteBuffersDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(MultiLingualAnalyzer()))
        val manager = SearcherManager(writer, SearcherFactory())
        val committer = LuceneSyncCommitter(writer, manager)
        val query = TermQuery(Term("type", "series"))
        val out = mutableListOf<Any>(DirectoryReader.indexExists(directory), manager.acquire().search(query, 100).scoreDocs.size)
        writer.addDocuments(SearchSamples.series.map { it.toDocument() })
        out.add(listOf(DirectoryReader.indexExists(directory), manager.acquire().search(query, 100).scoreDocs.size))
        committer.commitAndMaybeRefresh()
        out.add(listOf(DirectoryReader.indexExists(directory), manager.acquire().search(query, 100).scoreDocs.size))
        writer.deleteDocuments(Term("series_id", "S1"))
        out.add(manager.acquire().search(query, 100).scoreDocs.size)
        committer.commitAndMaybeRefresh()
        out.add(manager.acquire().search(query, 100).scoreDocs.size)
        committer.commitAndMaybeRefresh()
        out.add(manager.acquire().search(query, 100).scoreDocs.size)
        out
      }
      case("nothing to commit") {
        val directory = ByteBuffersDirectory()
        val writer = IndexWriter(directory, IndexWriterConfig(MultiLingualAnalyzer()))
        LuceneSyncCommitter(writer, SearcherManager(writer, SearcherFactory())).commitAndMaybeRefresh()
        DirectoryReader.indexExists(directory)
      }
    }
  }
}
