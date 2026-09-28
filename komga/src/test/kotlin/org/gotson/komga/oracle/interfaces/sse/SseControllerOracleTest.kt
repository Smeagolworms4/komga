package org.gotson.komga.oracle.interfaces.sse

import org.gotson.komga.application.tasks.Task
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.DomainEvent
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.ReadProgress
import org.gotson.komga.domain.model.Series
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.ThumbnailReadList
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.domain.model.ThumbnailSeriesCollection
import org.gotson.komga.infrastructure.security.KomgaPrincipal
import org.gotson.komga.interfaces.sse.SseController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.WebOracle
import org.gotson.komga.oracle.interfaces.InterfacesData
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.net.URL
import java.time.LocalDateTime

class SseControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val controller = SseController(db.bookDao, db.tasksDao)
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)
  private val emitters = mutableListOf<Pair<String, SseEmitter>>()

  /** text sent to each emitter since the last call (buffered until the emitter is initialized by Spring MVC) */
  @Suppress("UNCHECKED_CAST")
  private fun drain(): List<List<String>> {
    val f = ResponseBodyEmitter::class.java.getDeclaredField("earlySendAttempts")
    f.isAccessible = true
    return emitters.map { (name, e) ->
      val sent = f.get(e) as MutableSet<ResponseBodyEmitter.DataWithMediaType>
      val text = sent.joinToString("") { if (it.data is String) it.data as String else WebOracle.mapper.writeValueAsString(it.data) }
      sent.clear()
      listOf(name, text)
    }
  }

  private val library = Library("L", URL("file:/l"), id = "L1")
  private val series = Series("S", URL("file:/l/s"), date, id = "S1", libraryId = "L1")
  private val book = Book("B", URL("file:/l/s/b.cbz"), date, id = "B1", seriesId = "S1", libraryId = "L1")
  private val readList = ReadList("R", bookIds = sortedMapOf(2 to "B2", 1 to "B1"), id = "R1")
  private val collection = SeriesCollection("C", seriesIds = listOf("S2", "S1"), id = "C1")
  private val dimension = Dimension(1, 1)

  private fun event(
    name: String,
    e: DomainEvent,
  ) = case(name) {
    controller.handleSseEvent(e)
    drain()
  }

  override fun cases() {
    func("sse") {
      case("setup") { InterfacesData.setup(db) }
      case("admin") { emitters.add("admin" to controller.sse(KomgaPrincipal(InterfacesData.admin))).let { emitters.size } }
      case("limited") { emitters.add("limited" to controller.sse(KomgaPrincipal(InterfacesData.limited))).let { emitters.size } }
      case("restricted") { emitters.add("restricted" to controller.sse(KomgaPrincipal(InterfacesData.restricted))).let { emitters.size } }
      case("nothing sent yet") { drain() }
    }
    func("heartbeat") {
      case("all emitters") {
        controller.heartbeat()
        drain()
      }
    }
    func("taskCount") {
      case("no task") {
        controller.taskCount()
        drain()
      }
      case("tasks") {
        db.tasksDao.save(Task.ScanLibrary("L1", false, 5))
        db.tasksDao.save(Task.ScanLibrary("L2", true, 4))
        db.tasksDao.save(Task.AnalyzeBook("B1", 3, "S1"))
        controller.taskCount()
        drain()
      }
    }
    func("handleSseEvent") {
      event("LibraryAdded", DomainEvent.LibraryAdded(library))
      event("LibraryUpdated", DomainEvent.LibraryUpdated(library))
      event("LibraryDeleted", DomainEvent.LibraryDeleted(library))
      event("LibraryScanned", DomainEvent.LibraryScanned(library))
      event("SeriesAdded", DomainEvent.SeriesAdded(series))
      event("SeriesUpdated", DomainEvent.SeriesUpdated(series))
      event("SeriesDeleted", DomainEvent.SeriesDeleted(series))
      event("BookAdded", DomainEvent.BookAdded(book))
      event("BookUpdated", DomainEvent.BookUpdated(book))
      event("BookDeleted", DomainEvent.BookDeleted(book))
      event("BookImported", DomainEvent.BookImported(book, URL("file:/import/a%20b/%C3%BC.cbz"), true))
      event("BookImported failed", DomainEvent.BookImported(null, URL("file:/import/x.cbz"), false, "ERR_1002"))
      event("ReadListAdded", DomainEvent.ReadListAdded(readList))
      event("ReadListUpdated", DomainEvent.ReadListUpdated(readList))
      event("ReadListDeleted", DomainEvent.ReadListDeleted(readList))
      event("CollectionAdded", DomainEvent.CollectionAdded(collection))
      event("CollectionUpdated", DomainEvent.CollectionUpdated(collection))
      event("CollectionDeleted", DomainEvent.CollectionDeleted(collection))
      event("ReadProgressChanged", DomainEvent.ReadProgressChanged(ReadProgress("B1", "U2", 1, false, date)))
      event("ReadProgressDeleted", DomainEvent.ReadProgressDeleted(ReadProgress("B1", "U1", 1, false, date)))
      event("ReadProgressSeriesChanged", DomainEvent.ReadProgressSeriesChanged("S1", "U3"))
      event("ReadProgressSeriesDeleted", DomainEvent.ReadProgressSeriesDeleted("S1", "UX"))
      event("ThumbnailBookAdded", DomainEvent.ThumbnailBookAdded(ThumbnailBook(type = ThumbnailBook.Type.GENERATED, mediaType = "image/jpeg", fileSize = 1, dimension = dimension, selected = true, bookId = "B2")))
      event("ThumbnailBookDeleted unknown book", DomainEvent.ThumbnailBookDeleted(ThumbnailBook(type = ThumbnailBook.Type.SIDECAR, mediaType = "image/jpeg", fileSize = 1, dimension = dimension, bookId = "BX")))
      event("ThumbnailSeriesAdded", DomainEvent.ThumbnailSeriesAdded(ThumbnailSeries(type = ThumbnailSeries.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = dimension, seriesId = "S1", selected = true)))
      event("ThumbnailSeriesDeleted", DomainEvent.ThumbnailSeriesDeleted(ThumbnailSeries(type = ThumbnailSeries.Type.SIDECAR, mediaType = "image/png", fileSize = 1, dimension = dimension, seriesId = "S2")))
      event("ThumbnailSeriesCollectionAdded", DomainEvent.ThumbnailSeriesCollectionAdded(ThumbnailSeriesCollection(ByteArray(0), type = ThumbnailSeriesCollection.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = dimension, collectionId = "C1")))
      event("ThumbnailSeriesCollectionDeleted", DomainEvent.ThumbnailSeriesCollectionDeleted(ThumbnailSeriesCollection(ByteArray(0), selected = true, type = ThumbnailSeriesCollection.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = dimension, collectionId = "C1")))
      event("ThumbnailReadListAdded", DomainEvent.ThumbnailReadListAdded(ThumbnailReadList(ByteArray(0), type = ThumbnailReadList.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = dimension, readListId = "R1")))
      event("ThumbnailReadListDeleted", DomainEvent.ThumbnailReadListDeleted(ThumbnailReadList(ByteArray(0), selected = true, type = ThumbnailReadList.Type.USER_UPLOADED, mediaType = "image/png", fileSize = 1, dimension = dimension, readListId = "R1")))
      event("UserUpdated without expiry", DomainEvent.UserUpdated(InterfacesData.limited, false))
      event("UserUpdated with expiry", DomainEvent.UserUpdated(InterfacesData.limited, true))
      event("UserDeleted", DomainEvent.UserDeleted(InterfacesData.restricted))
    }
    func("emitSse") {
      event("admin only", DomainEvent.BookImported(book, URL("file:/i.cbz"), true, "msg \"quoted\"\nline"))
      event("user only", DomainEvent.ReadProgressChanged(ReadProgress("B1", "U1", 1, true, date)))
    }
    func("start") {
      case("no-op") { controller.start() }
    }
    func("isRunning") {
      case("always") { controller.isRunning }
    }
    func("getPhase") {
      case("default phase") { controller.phase }
    }
    func("stop") {
      case("stop") {
        controller.stop()
        drain()
      }
      case("new connection refused") { controller.sse(KomgaPrincipal(InterfacesData.admin)) }
      case("send after stop") {
        controller.handleSseEvent(DomainEvent.LibraryAdded(library))
        "sent"
      }
      case("stop twice") { controller.stop() }
    }
  }
}
