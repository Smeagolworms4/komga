package org.gotson.komga.oracle.domain.service

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ReadList
import org.gotson.komga.domain.model.SearchContext
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.model.ThumbnailReadList
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.main.DaoSeed
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.book
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.date
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.library
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.metadata
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.resource
import org.gotson.komga.oracle.domain.service.ServiceGraph.Companion.series
import org.springframework.data.domain.Pageable

class ReadListLifecycleOracleTest : OracleTest() {
  private val db = OracleDb()
  private val graph = ServiceGraph(db)
  private val lifecycle = graph.readListLifecycle

  private fun rl(
    id: String,
    name: String = "rl $id",
    vararg books: Pair<Int, String>,
  ) = ReadList(name = name, bookIds = sortedMapOf(*books), id = id, createdDate = date)

  private fun thumb(
    id: String,
    readListId: String,
    selected: Boolean = false,
  ) = ThumbnailReadList(
    thumbnail = oracleBytes(8),
    selected = selected,
    type = ThumbnailReadList.Type.USER_UPLOADED,
    mediaType = "image/jpeg",
    fileSize = 8,
    dimension = Dimension(1, 2),
    id = id,
    readListId = readListId,
    createdDate = date,
  )

  private fun find(id: String) = db.readListDao.findByIdOrNull(id, SearchContext.empty())

  private fun byName(name: String) = db.readListDao.findByNameOrNull(name)

  private fun thumbs() = db.rawQuery("select ID, READLIST_ID, SELECTED from THUMBNAIL_READLIST order by ID")

  private fun readLists() = db.readListDao.findAll(SearchContext.empty(), Pageable.unpaged()).content.map { listOf(it.name, it.bookIds) }

  private fun b(id: String) = db.bookDao.findByIdOrNull(id)!!

  override fun cases() {
    func("addReadList") {
      case("setup") {
        db.libraryDao.insert(library("L1"))
        db.seriesDao.insert(series("S1", "L1"))
        db.seriesMetadataDao.insert(SeriesMetadata(title = "Batman", seriesId = "S1", createdDate = date))
        (1..6).forEach {
          val bk = book("B$it", "S1", "L1", number = it)
          db.bookDao.insert(bk)
          db.bookMetadataDao.insert(metadata(bk))
        }
        db.bookDao.count()
      }
      case("new read list") { stable(listOf(lifecycle.addReadList(rl("R1", "rl R1", 1 to "B2", 0 to "B1")), graph.takeEvents())) }
      case("duplicate name") { lifecycle.addReadList(rl("R2", "rl R1")) }
      case("duplicate name other case") { lifecycle.addReadList(rl("R2", "RL r1")) }
      case("empty read list") { stable(listOf(lifecycle.addReadList(rl("R2")), graph.takeEvents())) }
      case("unknown book") { exceptionType { DaoSeed.transactional(db) { lifecycle.addReadList(rl("R3", "rl R3", 0 to "B9")) } } }
      case("after errors") { stable(listOf(readLists(), graph.takeEvents())) }
    }
    func("updateReadList") {
      case("unknown read list") { lifecycle.updateReadList(rl("R9")) }
      case("rename to existing name") { lifecycle.updateReadList(rl("R2", "rl R1")) }
      case("rename to own name other case") {
        lifecycle.updateReadList(find("R1")!!.copy(name = "RL R1"))
        stable(listOf(find("R1"), graph.takeEvents()))
      }
      case("change books") {
        lifecycle.updateReadList(find("R1")!!.copy(bookIds = sortedMapOf(5 to "B3", 2 to "B1"), summary = "sum", ordered = false))
        stable(listOf(find("R1"), graph.takeEvents()))
      }
    }
    func("addBookToReadList") {
      case("already in read list") {
        lifecycle.addBookToReadList("RL R1", b("B1"), 7)
        stable(listOf(find("R1"), graph.takeEvents()))
      }
      case("free position") {
        lifecycle.addBookToReadList("RL R1", b("B4"), 3)
        stable(listOf(find("R1"), graph.takeEvents()))
      }
      case("taken position goes last") {
        lifecycle.addBookToReadList("rl r1", b("B5"), 2)
        stable(listOf(find("R1"), graph.takeEvents()))
      }
      case("null position goes last") {
        lifecycle.addBookToReadList("RL R1", b("B6"), null)
        stable(listOf(find("R1"), graph.takeEvents()))
      }
      case("new read list with position") {
        lifecycle.addBookToReadList("Brand new", b("B1"), 4)
        stable(listOf(byName("Brand new"), graph.takeEvents()))
      }
      case("new read list without position") {
        lifecycle.addBookToReadList("Other new", b("B2"), null)
        stable(listOf(byName("Other new"), graph.takeEvents()))
      }
      case("existing empty read list with null position") {
        exceptionType { lifecycle.addBookToReadList("rl R2", b("B2"), null) }
      }
      case("existing empty read list with position") {
        lifecycle.addBookToReadList("rl R2", b("B2"), -1)
        stable(listOf(find("R2"), graph.takeEvents()))
      }
    }
    func("addThumbnail") {
      case("not selected") { stable(listOf(lifecycle.addThumbnail(thumb("T1", "R1")), thumbs(), graph.takeEvents())) }
      case("selected") { stable(listOf(lifecycle.addThumbnail(thumb("T2", "R1", true)), thumbs(), graph.takeEvents())) }
      case("selected replaces selection") { stable(listOf(lifecycle.addThumbnail(thumb("T3", "R1", true)), thumbs(), graph.takeEvents())) }
      case("other read list") { stable(listOf(lifecycle.addThumbnail(thumb("T4", "R2")), thumbs(), graph.takeEvents())) }
      case("unknown read list") { exceptionType { lifecycle.addThumbnail(thumb("T5", "R9")) } }
    }
    func("markSelectedThumbnail") {
      case("select T1") {
        lifecycle.markSelectedThumbnail(thumb("T1", "R1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("unknown thumbnail") {
        lifecycle.markSelectedThumbnail(thumb("T9", "R1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("deleteThumbnail") {
      case("selected one") {
        lifecycle.deleteThumbnail(thumb("T1", "R1", true))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("not selected one") {
        lifecycle.deleteThumbnail(thumb("T2", "R1"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("unknown thumbnail") {
        lifecycle.deleteThumbnail(thumb("T9", "R2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("thumbnailsHouseKeeping") {
      case("only unselected left") {
        db.thumbnailReadListDao.insert(thumb("T6", "R2"))
        lifecycle.deleteThumbnail(thumb("T9", "R2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("several selected") {
        db.thumbnailReadListDao.insert(thumb("T7", "R2", true))
        db.thumbnailReadListDao.insert(thumb("T8", "R2", true))
        lifecycle.deleteThumbnail(thumb("T9", "R2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
      case("none left") {
        listOf("T4", "T6", "T7").forEach { db.thumbnailReadListDao.delete(it) }
        lifecycle.deleteThumbnail(thumb("T8", "R2"))
        stable(listOf(thumbs(), graph.takeEvents()))
      }
    }
    func("getThumbnailBytes") {
      case("selected thumbnail") { lifecycle.getThumbnailBytes(find("R1")!!) }
      case("mosaic without book thumbnail") {
        db.thumbnailReadListDao.deleteByReadListId("R1")
        graph.describeImage(lifecycle.getThumbnailBytes(find("R1")!!))
      }
      case("mosaic with book thumbnails") {
        val png = resource("barcode/komga.png")
        listOf("B1", "B3").forEach {
          db.thumbnailBookDao.insert(
            ThumbnailBook(thumbnail = png, selected = true, type = ThumbnailBook.Type.GENERATED, mediaType = "image/png", fileSize = png.size.toLong(), dimension = Dimension(1, 1), id = "TB$it", bookId = it, createdDate = date),
          )
        }
        graph.describeImage(lifecycle.getThumbnailBytes(find("R1")!!))
      }
      case("single book repeated") { graph.describeImage(lifecycle.getThumbnailBytes(rl("RX", "x", 0 to "B3"))) }
      case("more than 4 books") { graph.describeImage(lifecycle.getThumbnailBytes(rl("RX", "x", 0 to "B2", 1 to "B4", 2 to "B5", 3 to "B6", 4 to "B1"))) }
    }
    func("matchComicRackList") {
      fun cbl(body: String) = "<?xml version=\"1.0\"?><ReadingList xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">$body</ReadingList>".toByteArray()
      case("matching books") {
        lifecycle.matchComicRackList(
          cbl("<Name>New list</Name><Books><Book Series=\"Batman\" Number=\"1\" Volume=\"2016\" Year=\"2016\"/><Book Series=\"batman\" Number=\"3\"/><Book Series=\"Robin\" Number=\"1\"/></Books>"),
        )
      }
      case("existing name") { lifecycle.matchComicRackList(cbl("<Name>RL R1</Name><Books/>")) }
      case("no name") { lifecycle.matchComicRackList(cbl("<Books/>")) }
      case("invalid xml") { lifecycle.matchComicRackList("not xml".toByteArray()) }
      case("empty") { lifecycle.matchComicRackList(ByteArray(0)) }
    }
    func("deleteReadList") {
      case("with thumbnail") {
        db.thumbnailReadListDao.insert(thumb("T10", "R2", true))
        lifecycle.deleteReadList(find("R2")!!)
        stable(listOf(find("R2"), thumbs(), graph.takeEvents()))
      }
      case("unknown read list") {
        lifecycle.deleteReadList(rl("R9"))
        stable(graph.takeEvents())
      }
    }
    func("deleteEmptyReadLists") {
      case("setup") {
        lifecycle.addReadList(rl("E1"))
        lifecycle.addReadList(rl("E2"))
        db.thumbnailReadListDao.insert(thumb("T11", "E1", true))
        graph.takeEvents()
        readLists()
      }
      case("deletes empty ones") {
        lifecycle.deleteEmptyReadLists()
        stable(listOf(readLists(), thumbs(), graph.takeEvents()))
      }
      case("nothing to delete") {
        lifecycle.deleteEmptyReadLists()
        stable(listOf(readLists(), graph.takeEvents()))
      }
    }
  }
}
