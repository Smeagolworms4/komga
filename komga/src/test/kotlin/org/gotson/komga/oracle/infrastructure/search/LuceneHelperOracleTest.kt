package org.gotson.komga.oracle.infrastructure.search

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.index.Term
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.toDocument
import org.gotson.komga.oracle.OracleTest

class LuceneHelperOracleTest : OracleTest() {
  private fun corpus(): List<Document> =
    SearchSamples.books.map { it.toDocument() } + SearchSamples.series.map { it.toDocument() } + SearchSamples.collections.map { it.toDocument() } +
      SearchSamples.readLists.map { it.toDocument() }

  private fun indexed() = SearchSamples.Index().also { it.helper.addDocuments(corpus()) }

  private fun versionDoc(v: String) =
    Document().apply {
      add(StringField("index_version", v, Field.Store.YES))
      add(StringField("type", "index_version", Field.Store.NO))
    }

  override fun cases() {
    func("indexExists") {
      case("new directory") { SearchSamples.Index().helper.indexExists() }
      case("after setIndexVersion") { SearchSamples.Index().helper.let { it.setIndexVersion(1) ; it.indexExists() } }
      case("after addDocuments") { indexed().helper.indexExists() }
      case("after addDocuments of nothing") { SearchSamples.Index().helper.let { it.addDocuments(emptyList()) ; it.indexExists() } }
    }
    func("setIndexVersion") {
      for (v in listOf(1, 8, 0, -1, Int.MAX_VALUE)) case("$v") { SearchSamples.Index().helper.let { it.setIndexVersion(v) ; it.getIndexVersion() } }
      case("twice") { SearchSamples.Index().helper.let { it.setIndexVersion(3) ; it.setIndexVersion(5) ; it.getIndexVersion() } }
      case("with documents") { indexed().let { it.helper.setIndexVersion(8) ; listOf(it.helper.getIndexVersion(), it.searchAll()) } }
    }
    func("getIndexVersion") {
      case("empty index") { SearchSamples.Index().helper.getIndexVersion() }
      for (v in listOf("abc", " 7", "+7", "-3", "99999999999", "", "007")) {
        case("stored '$v'") { SearchSamples.Index().helper.let { it.updateDocument(Term("type", "index_version"), versionDoc(v)) ; it.getIndexVersion() } }
      }
    }
    func("searchEntitiesIds") {
      case("empty index") { SearchSamples.Index().searchAll() }
      case("corpus") { indexed().searchAll() }
    }
    func("upgradeIndex") {
      case("corpus: the IndexWriter holds the lock") { indexed().let { listOf(exceptionType { it.helper.upgradeIndex() }, it.helper.indexExists(), it.searchAll(listOf(LuceneEntity.Book))) } }
      case("empty directory") { exceptionType { SearchSamples.Index().helper.upgradeIndex() } }
    }
    func("addDocument") {
      case("one by one") {
        SearchSamples.Index().let { index ->
          corpus().take(3).map { d ->
            index.helper.addDocument(d)
            index.searchAll(listOf(LuceneEntity.Book))
          }
        }
      }
      case("same document twice") { SearchSamples.Index().let { it.helper.addDocument(SearchSamples.books[0].toDocument()) ; it.helper.addDocument(SearchSamples.books[0].toDocument()) ; it.searchAll(listOf(LuceneEntity.Book)) } }
      case("empty document") { SearchSamples.Index().let { it.helper.addDocument(Document()) ; listOf(it.helper.indexExists(), it.searchAll(listOf(LuceneEntity.Book))) } }
    }
    func("addDocuments") {
      case("corpus") { indexed().searchAll(listOf(LuceneEntity.Series, LuceneEntity.Collection)) }
      case("in two batches") { SearchSamples.Index().let { it.helper.addDocuments(corpus().take(8)) ; it.helper.addDocuments(corpus().drop(8)) ; it.searchAll() } }
    }
    func("updateDocument") {
      case("replace a book") {
        indexed().let {
          it.helper.updateDocument(Term("book_id", "B1"), SearchSamples.books[1].toDocument())
          it.searchAllSorted(listOf(LuceneEntity.Book))
        }
      }
      case("unknown term adds") { indexed().let { it.helper.updateDocument(Term("book_id", "none"), SearchSamples.series[0].toDocument()) ; it.searchAllSorted(listOf(LuceneEntity.Series)) } }
      case("term matching several documents") { indexed().let { it.helper.updateDocument(Term("type", "book"), SearchSamples.books[3].toDocument()) ; it.searchAllSorted(listOf(LuceneEntity.Book)) } }
    }
    func("deleteDocuments") {
      case("one book") { indexed().let { it.helper.deleteDocuments(Term("book_id", "B1")) ; it.searchAllSorted(listOf(LuceneEntity.Book)) } }
      case("all series") { indexed().let { it.helper.deleteDocuments(Term("type", "series")) ; it.searchAllSorted(listOf(LuceneEntity.Series, LuceneEntity.Book)) } }
      case("unknown term") { indexed().let { it.helper.deleteDocuments(Term("nothing", "x")) ; it.searchAllSorted(listOf(LuceneEntity.ReadList)) } }
      case("tokenized field term") { indexed().let { it.helper.deleteDocuments(Term("title", "batman")) ; it.searchAllSorted(listOf(LuceneEntity.Book, LuceneEntity.Series)) } }
    }
  }
}
