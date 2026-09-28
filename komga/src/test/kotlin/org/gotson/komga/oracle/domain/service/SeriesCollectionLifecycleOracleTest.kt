package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SeriesCollection
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.domain.model.ThumbnailSeriesCollection
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series

class SeriesCollectionLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.seriesCollectionLifecycle

  private fun col(
    id: String,
    name: String = "col $id",
    seriesIds: List<String> = emptyList(),
  ) = SeriesCollection(name = name, seriesIds = seriesIds, id = id, createdDate = date)

  private fun thumb(
    id: String,
    collectionId: String,
    selected: Boolean = false,
  ) = ThumbnailSeriesCollection(
    thumbnail = oracleBytes(8),
    selected = selected,
    type = ThumbnailSeriesCollection.Type.USER_UPLOADED,
    mediaType = "image/jpeg",
    fileSize = 8,
    dimension = Dimension(1, 2),
    id = id,
    collectionId = collectionId,
    createdDate = date,
  )

  private fun find(id: String) = db.seriesCollectionDao.findByIdOrNull(id, SearchContext.empty())

  private fun thumbs() = db.rawQuery("select ID, COLLECTION_ID, SELECTED from THUMBNAIL_COLLECTION order by ID")

  private fun collections() = db.seriesCollectionDao.findAll(SearchContext.empty(), org.springframework.data.domain.Pageable.unpaged()).content.map { listOf(it.name, it.seriesIds) }

  override fun cases() {
    func("addCollection") {
      case("setup") {
        db.libraryDao.insert(library("L1"))
        (1..6).forEach { db.seriesDao.insert(series("S$it", "L1")) }
        db.seriesDao.count()
      }
      case("new collection") { stable(listOf(lifecycle.addCollection(col("C1", seriesIds = listOf("S2", "S1"))), graph.takeEvents())) }
      case("duplicate name") { lifecycle.addCollection(col("C2", name = "col C1")) }
      case("duplicate name other case") { lifecycle.addCollection(col("C2", name = "COL c1")) }
      case("empty collection") { stable(listOf(lifecycle.addCollection(col("C2")), graph.takeEvents())) }
      case("unknown series") { exceptionType { DaoSeed.transactional(db) { lifecycle.addCollection(col("C3", seriesIds = listOf("S9"))) } } }
      case("duplicate id") { exceptionType { lifecycle.addCollection(col("C1", name = "other")) } }
      case("after errors") { stable(listOf(collections(), graph.takeEvents())) }
    }
    func("updateCollection") {
      case("unknown collection") { lifecycle.updateCollection(col("C9")) }
      case("rename to existing name") { lifecycle.updateCollection(col("C2", name = "col C1")) }
      case("rename to own name other case") {
        lifecycle.updateCollection(find("C1")!!.copy(name = "COL C1"))
        stable(listOf(find("C1"), graph.takeEvents()))
      }
      case("change series and order") {
        lifecycle.updateCollection(find("C1")!!.copy(seriesIds = listOf("S3", "S1", "S2"), ordered = true))
        stable(listOf(find("C1"), graph.takeEvents()))
      }
    }
    func("addSeriesToCollection") {
      case("already in collection") {
        lifecycle.addSeriesToCollection("COL C1", db.seriesDao.findByIdOrNull("S1")!!)
        stable(listOf(find("C1"), graph.takeEvents()))
      }
      case("existing collection") {
        lifecycle.addSeriesToCollection("COL C1", db.seriesDao.findByIdOrNull("S4")!!)
        stable(listOf(find("C1"), graph.takeEvents()))
      }
      case("existing collection, name case differs") {
        lifecycle.addSeriesToCollection("col c1", db.seriesDao.findByIdOrNull("S5")!!)
        stable(listOf(find("C1"), graph.takeEvents()))
      }
      case("new collection") {
        lifecycle.addSeriesToCollection("Brand new", db.seriesDao.findByIdOrNull("S1")!!)
        stable(listOf(db.seriesCollectionDao.findByNameOrNull("Brand new"), graph.takeEvents()))
      }
      case("empty name") {
        lifecycle.addSeriesToCollection("", db.seriesDao.findByIdOrNull("S1")!!)
        stable(listOf(db.seriesCollectionDao.findByNameOrNull(""), graph.takeEvents()))
      }
    }
    func("addThumbnail") {
      case("not selected") { stable(listOf(lifecycle.addThumbnail(thumb("T1", "C1")), thumbs(), graph.takeEvents())) }
      case("selected") { stable(listOf(lifecycle.addThumbnail(thumb("T2", "C1", true)), thumbs(), graph.takeEvents())) }
      case("selected replaces selection") { stable(listOf(lifecycle.addThumbnail(thumb("T3", "C1", true)), thumbs(), graph.takeEvents())) }
      case("other collection") { stable(listOf(lifecycle.addThumbnail(thumb("T4", "C2")), thumbs(), graph.takeEvents())) }
      case("unknown collection") { exceptionType { lifecycle.addThumbnail(thumb("T5", "C9")) } }
    }
    func("markSelectedThumbnail") {
      case("select T1") {
        lifecycle.markSelectedThumbnail(thumb("T1", "C1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("unknown thumbnail") {
        lifecycle.markSelectedThumbnail(thumb("T9", "C1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("deleteThumbnail") {
      case("selected one") {
        lifecycle.deleteThumbnail(thumb("T1", "C1", true))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("not selected one") {
        lifecycle.deleteThumbnail(thumb("T2", "C1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("unknown thumbnail") {
        lifecycle.deleteThumbnail(thumb("T9", "C2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("thumbnailsHouseKeeping") {
      case("only unselected left") {
        db.thumbnailSeriesCollectionDao.insert(thumb("T6", "C2"))
        lifecycle.deleteThumbnail(thumb("T9", "C2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("several selected") {
        db.thumbnailSeriesCollectionDao.insert(thumb("T7", "C2", true))
        db.thumbnailSeriesCollectionDao.insert(thumb("T8", "C2", true))
        lifecycle.deleteThumbnail(thumb("T9", "C2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("none left") {
        listOf("T4", "T6", "T7").forEach { db.thumbnailSeriesCollectionDao.delete(it) }
        lifecycle.deleteThumbnail(thumb("T8", "C2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("getThumbnailBytes") {
      case("selected thumbnail") { lifecycle.getThumbnailBytes(find("C1")!!, "U1") }
      case("mosaic without series thumbnail") {
        db.thumbnailSeriesCollectionDao.deleteByCollectionId("C1")
        graph.describeImage(lifecycle.getThumbnailBytes(find("C1")!!, "U1"))
      }
      case("mosaic with series thumbnails") {
        val png = resource("barcode/komga.png")
        listOf("S1", "S3").forEach {
          db.thumbnailSeriesDao.insert(
            ThumbnailSeries(thumbnail = png, selected = true, type = ThumbnailSeries.Type.USER_UPLOADED, mediaType = "image/png", fileSize = png.size.toLong(), dimension = Dimension(1, 1), id = "TS$it", seriesId = it, createdDate = date),
          )
        }
        graph.describeImage(lifecycle.getThumbnailBytes(find("C1")!!, "U1"))
      }
      case("single series repeated") { graph.describeImage(lifecycle.getThumbnailBytes(col("CX", seriesIds = listOf("S3")), "U1")) }
      case("more than 4 series") { graph.describeImage(lifecycle.getThumbnailBytes(col("CX", seriesIds = listOf("S2", "S4", "S5", "S6", "S1")), "U1")) }
    }
    func("deleteCollection") {
      case("with thumbnail") {
        db.thumbnailSeriesCollectionDao.insert(thumb("T10", "C2", true))
        lifecycle.deleteCollection(find("C2")!!)
        stable(listOf(find("C2"), thumbs(), graph.takeEvents()))
      }
      case("unknown collection") {
        lifecycle.deleteCollection(col("C9"))
        stable(graph.takeEvents())
      }
    }
    func("deleteEmptyCollections") {
      case("setup") {
        lifecycle.addCollection(col("E1"))
        lifecycle.addCollection(col("E2"))
        db.thumbnailSeriesCollectionDao.insert(thumb("T11", "E1", true))
        graph.takeEvents()
        collections()
      }
      case("deletes empty ones") {
        lifecycle.deleteEmptyCollections()
        stable(listOf(collections(), thumbs(), graph.takeEvents()))
      }
      case("nothing to delete") {
        lifecycle.deleteEmptyCollections()
        stable(listOf(collections(), graph.takeEvents()))
      }
    }
  }
}
