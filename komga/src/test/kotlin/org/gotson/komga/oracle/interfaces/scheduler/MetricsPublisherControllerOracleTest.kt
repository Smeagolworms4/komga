package org.gotson.komga.oracle.interfaces.scheduler

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.DomainEvent
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.interfaces.scheduler.MetricsPublisherController
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.interfaces.InterfacesData
import java.net.URL
import java.time.LocalDateTime

class MetricsPublisherControllerOracleTest : OracleTest() {
  private val db = OracleDb()
  private val registry = SimpleMeterRegistry()
  private val controller = MetricsPublisherController(db.libraryDao, db.bookDao, db.seriesDao, db.seriesCollectionDao, db.readListDao, db.sidecarDao, registry)

  private fun meters(): List<List<Any?>> =
    registry.meters
      .map {
        val tags = it.id.tags.joinToString(",") { t -> "${t.key}=${t.value}" }
        listOf("${it.id.name}{$tags}", it.id.type.name, it.id.description, it.id.baseUnit, (it as? Gauge)?.value())
      }.sortedBy { it[0] as String }

  private fun call(
    name: String,
    arg: Any,
  ) {
    val m = MetricsPublisherController::class.java.declaredMethods.first { it.name == name }
    m.isAccessible = true
    m.invoke(controller, arg)
  }

  private val library = Library("X", URL("file:/x"), id = "LX")
  private val date = LocalDateTime.of(2020, 1, 1, 0, 0)

  override fun cases() {
    func("pushAllMetrics") {
      case("after init") { meters() }
      case("empty database") {
        controller.pushAllMetrics()
        meters()
      }
      case("with data") {
        InterfacesData.setup(db)
        controller.pushAllMetrics()
        meters()
      }
    }
    func("pushMetricsOnEvent") {
      case("library added") {
        call("pushMetricsOnEvent", DomainEvent.LibraryAdded(library))
        meters()
      }
      case("collection added twice, read list added") {
        call("pushMetricsOnEvent", DomainEvent.CollectionAdded(SeriesCollection("c")))
        call("pushMetricsOnEvent", DomainEvent.CollectionAdded(SeriesCollection("c")))
        call("pushMetricsOnEvent", DomainEvent.ReadListAdded(ReadList("r")))
        meters()
      }
      case("deleted") {
        call("pushMetricsOnEvent", DomainEvent.CollectionDeleted(SeriesCollection("c")))
        call("pushMetricsOnEvent", DomainEvent.ReadListDeleted(ReadList("r")))
        call("pushMetricsOnEvent", DomainEvent.ReadListDeleted(ReadList("r")))
        meters()
      }
      case("book added then library scanned") {
        db.bookDao.insert(Book("b7", URL("file:/data/manga%20co/One%20Piece/b7.cbz"), date, fileSize = 5, id = "B7", seriesId = "S2", libraryId = "L2", createdDate = date))
        call("pushMetricsOnEvent", DomainEvent.LibraryScanned(library))
        meters()
      }
      case("library deleted") {
        call("pushMetricsOnEvent", DomainEvent.LibraryDeleted(library))
        meters()
      }
      case("other event") {
        call("pushMetricsOnEvent", DomainEvent.BookAdded(Book("b", URL("file:/b"), date)))
        meters()
      }
    }
    func("pushMetricsCount") {
      listOf("libraries", "series", "books", "books.filesize", "collections", "readlists", "sidecars", "unknown").forEach { e ->
        case(e) {
          call("pushMetricsCount", e)
          meters()
        }
      }
    }
  }
}
