package org.gotson.komga.oracle.infrastructure.search

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.DomainEvent
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.persistence.ReadListRepository
import org.gotson.komga.domain.persistence.SeriesCollectionRepository
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.SearchIndexLifecycle
import org.gotson.komga.interfaces.api.persistence.BookDtoRepository
import org.gotson.komga.interfaces.api.persistence.SeriesDtoRepository
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.gotson.komga.interfaces.api.rest.dto.SeriesDto
import org.gotson.komga.oracle.OracleTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.net.URL
import java.time.LocalDateTime

class SearchIndexLifecycleOracleTest : OracleTest() {
  /** Repositories backed by mutable lists, a real LuceneHelper on an in-memory index */
  private class Env {
    val books = SearchSamples.books.toMutableList()
    val series = SearchSamples.series.toMutableList()
    val collections = SearchSamples.collections.toMutableList()
    val readLists = SearchSamples.readLists.toMutableList()
    val index = SearchSamples.Index()

    private fun <T> page(
      list: List<T>,
      p: Pageable,
    ) = PageImpl(list.drop(p.offset.toInt()).take(p.pageSize), p, list.size.toLong())

    val lifecycle =
      SearchIndexLifecycle(
        mockk<SeriesCollectionRepository> {
          every { findAll(any(), any(), any(), any()) } answers { page(collections, secondArg()) }
          every { findByIdOrNull(any(), any()) } answers { collections.firstOrNull { it.id == firstArg<String>() } }
        },
        mockk<ReadListRepository> {
          every { findAll(any(), any(), any(), any()) } answers { page(readLists, secondArg()) }
          every { findByIdOrNull(any(), any()) } answers { readLists.firstOrNull { it.id == firstArg<String>() } }
        },
        mockk<BookDtoRepository> {
          every { findAll(any<Pageable>()) } answers { page(books, firstArg()) }
          every { findByIdOrNull(any(), any()) } answers { books.firstOrNull { it.id == firstArg<String>() } }
        },
        mockk<SeriesDtoRepository> {
          every { findAll(any<Pageable>()) } answers { page(series, firstArg()) }
          every { findByIdOrNull(any(), any()) } answers { series.firstOrNull { it.id == firstArg<String>() } }
        },
        index.helper,
      )

    fun state() = listOf(index.helper.getIndexVersion(), index.searchAll())

    fun stateSorted() = listOf(index.helper.getIndexVersion(), index.searchAllSorted())
  }

  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  private fun book(id: String) = Book("b", URL("file:/komga/$id.cbz"), date, id = id, createdDate = date)

  private fun series(id: String) = Series("s", URL("file:/komga/$id"), date, id = id, createdDate = date)

  private fun collection(id: String) = SeriesCollection("c", id = id, createdDate = date)

  private fun readList(id: String) = ReadList("r", id = id, createdDate = date)

  private fun renamed(b: BookDto) = b.copy(metadata = b.metadata.copy(title = "Renamed ${b.metadata.title}"))

  private fun renamed(s: SeriesDto) = s.copy(metadata = s.metadata.copy(title = "Renamed ${s.metadata.title}"))

  override fun cases() {
    func("upgradeIndex") {
      case("empty index") { Env().let { exceptionType { it.lifecycle.upgradeIndex() } } }
      case("after rebuild") {
        Env().let {
          it.lifecycle.rebuildIndex()
          listOf(exceptionType { it.lifecycle.upgradeIndex() }, it.state())
        }
      }
    }
    func("rebuildIndex@40") {
      case("all entities") { Env().let { it.lifecycle.rebuildIndex() ; it.state() } }
      for (e in LuceneEntity.entries) case("only $e") { Env().let { it.lifecycle.rebuildIndex(setOf(e)) ; it.state() } }
      case("no entity") { Env().let { it.lifecycle.rebuildIndex(emptySet()) ; it.state() } }
      case("twice") { Env().let { it.lifecycle.rebuildIndex() ; it.lifecycle.rebuildIndex() ; it.stateSorted() } }
      case("empty repositories") {
        Env().let {
          it.books.clear()
          it.series.clear()
          it.collections.clear()
          it.readLists.clear()
          it.lifecycle.rebuildIndex()
          it.state()
        }
      }
    }
    // private: the rebuild of one entity, with more than one page of 5000 entities
    func("rebuildIndex@57") {
      case("5001 collections") {
        Env().let {
          it.collections.clear()
          for (i in 1..5001) it.collections += SeriesCollection("collection $i", id = "C$i", createdDate = date)
          it.lifecycle.rebuildIndex(setOf(LuceneEntity.Collection))
          listOf(
            it.index.helper.searchEntitiesIds("collection", LuceneEntity.Collection)?.size,
            it.index.helper.searchEntitiesIds("5001", LuceneEntity.Collection),
            it.index.helper.searchEntitiesIds("\"collection 1\"", LuceneEntity.Collection)?.size,
          )
        }
      }
      case("previous documents of the entity are deleted") {
        Env().let {
          it.lifecycle.rebuildIndex()
          it.readLists.removeAt(0)
          it.books.removeAt(0)
          it.lifecycle.rebuildIndex(setOf(LuceneEntity.ReadList))
          it.stateSorted()
        }
      }
    }
    func("consumeEvents") {
      val events =
        listOf(
          "SeriesAdded S1" to DomainEvent.SeriesAdded(series("S1")),
          "SeriesAdded unknown" to DomainEvent.SeriesAdded(series("X")),
          "SeriesUpdated S2" to DomainEvent.SeriesUpdated(series("S2")),
          "SeriesUpdated unknown" to DomainEvent.SeriesUpdated(series("X")),
          "SeriesDeleted S1" to DomainEvent.SeriesDeleted(series("S1")),
          "BookAdded B1" to DomainEvent.BookAdded(book("B1")),
          "BookAdded oneshot B5" to DomainEvent.BookAdded(book("B5")),
          "BookAdded unknown" to DomainEvent.BookAdded(book("X")),
          "BookUpdated B2" to DomainEvent.BookUpdated(book("B2")),
          "BookUpdated oneshot B5" to DomainEvent.BookUpdated(book("B5")),
          "BookDeleted B1" to DomainEvent.BookDeleted(book("B1")),
          "ReadListAdded R1" to DomainEvent.ReadListAdded(readList("R1")),
          "ReadListUpdated R2" to DomainEvent.ReadListUpdated(readList("R2")),
          "ReadListUpdated unknown" to DomainEvent.ReadListUpdated(readList("X")),
          "ReadListDeleted R1" to DomainEvent.ReadListDeleted(readList("R1")),
          "CollectionAdded C1" to DomainEvent.CollectionAdded(collection("C1")),
          "CollectionUpdated C2" to DomainEvent.CollectionUpdated(collection("C2")),
          "CollectionDeleted C1" to DomainEvent.CollectionDeleted(collection("C1")),
          "other event" to DomainEvent.LibraryAdded(Library("l", URL("file:/komga/l"), id = "L", createdDate = date)),
        )
      for ((label, event) in events) {
        case("$label on empty index") { Env().let { it.lifecycle.consumeEvents(event) ; it.state() } }
        case("$label on rebuilt index, entities renamed") {
          Env().let {
            it.lifecycle.rebuildIndex()
            it.books.replaceAll { b -> renamed(b) }
            it.series.replaceAll { s -> renamed(s) }
            it.collections.replaceAll { c -> c.copy(name = "Renamed ${c.name}") }
            it.readLists.replaceAll { r -> r.copy(name = "Renamed ${r.name}") }
            it.lifecycle.consumeEvents(event)
            it.stateSorted()
          }
        }
      }
    }
    // private: oneshot books get the fields of their series
    func("bookToDocument") {
      case("oneshot book with its series") { Env().let { it.lifecycle.consumeEvents(DomainEvent.BookAdded(book("B5"))) ; it.index.searchAll(listOf(LuceneEntity.Book)) } }
      case("oneshot book without its series") { Env().let { env -> env.series.removeIf { it.id == "S5" } ; exceptionType { env.lifecycle.consumeEvents(DomainEvent.BookAdded(book("B5"))) } } }
      case("rebuild with a oneshot book without its series") { Env().let { env -> env.series.removeIf { it.id == "S5" } ; exceptionType { env.lifecycle.rebuildIndex(setOf(LuceneEntity.Book)) } } }
    }
    // private: through consumeEvents
    func("addEntity") {
      case("same book added twice") { Env().let { it.lifecycle.consumeEvents(DomainEvent.BookAdded(book("B1"))) ; it.lifecycle.consumeEvents(DomainEvent.BookAdded(book("B1"))) ; it.index.searchAll(listOf(LuceneEntity.Book)) } }
    }
    func("updateEntity") {
      case("update of a missing document adds it") { Env().let { it.lifecycle.consumeEvents(DomainEvent.CollectionUpdated(collection("C3"))) ; it.index.searchAll(listOf(LuceneEntity.Collection)) } }
    }
    func("deleteEntity") {
      case("delete of every entity type") {
        Env().let { env ->
          env.lifecycle.rebuildIndex()
          env.lifecycle.consumeEvents(DomainEvent.BookDeleted(book("B2")))
          env.lifecycle.consumeEvents(DomainEvent.SeriesDeleted(series("S2")))
          env.lifecycle.consumeEvents(DomainEvent.CollectionDeleted(collection("C2")))
          env.lifecycle.consumeEvents(DomainEvent.ReadListDeleted(readList("R2")))
          env.stateSorted()
        }
      }
    }
  }
}
