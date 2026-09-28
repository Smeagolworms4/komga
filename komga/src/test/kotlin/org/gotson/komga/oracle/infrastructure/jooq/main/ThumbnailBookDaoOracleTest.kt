package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL

class ThumbnailBookDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.thumbnailBookDao

  private fun th(
    id: String,
    owner: String = "B2",
    selected: Boolean = false,
    size: Int = 10,
    type: ThumbnailBook.Type = ThumbnailBook.Type.GENERATED,
  ) = ThumbnailBook(
    thumbnail = if (type == ThumbnailBook.Type.SIDECAR) null else oracleBytes(size),
    url = if (type == ThumbnailBook.Type.SIDECAR) URL("file:/lib1/S1/cover%20$id.jpg") else null,
    selected = selected,
    type = type,
    mediaType = "image/jpeg",
    fileSize = size.toLong(),
    dimension = Dimension(size, size * 2),
    id = id,
    bookId = owner,
  )

  private fun ids(owner: String) = dao.findAllByBookId(owner).map { it.id to it.selected }

  override fun cases() {
    func("findAllByBookId") {
      case("seeded thumbnails") {
        NzDaoSeed.seed(db)
        dao.findAllByBookId("B1")
      }
      case("none") { dao.findAllByBookId("B2") }
      case("unknown") { dao.findAllByBookId("NOPE") }
    }

    func("insert") {
      case("insert and read back") {
        dao.insert(th("T1", selected = true))
        stable(dao.findByIdOrNull("T1"))
      }
      case("sidecar with url") {
        dao.insert(th("T2", size = 3, type = ThumbnailBook.Type.SIDECAR))
        stable(dao.findByIdOrNull("T2"))
      }
      case("user uploaded") {
        dao.insert(th("T3", size = 700, type = ThumbnailBook.Type.USER_UPLOADED))
        ids("B2")
      }
      case("duplicate id") { exceptionType { dao.insert(th("T1")) } }
      case("unknown owner") { exceptionType { dao.insert(th("T9", "NOPE")) } }
    }

    func("findAllByBookIdAndType") {
      case("one type") { dao.findAllByBookIdAndType("B2", setOf(ThumbnailBook.Type.SIDECAR)).map { it.id } }
      case("two types") { dao.findAllByBookIdAndType("B2", setOf(ThumbnailBook.Type.GENERATED, ThumbnailBook.Type.USER_UPLOADED)).map { it.id } }
      case("empty set") { dao.findAllByBookIdAndType("B2", emptySet()) }
      case("no match") { dao.findAllByBookIdAndType("B6", setOf(ThumbnailBook.Type.SIDECAR)) }
    }

    func("findByIdOrNull") {
      case("seeded") { dao.findByIdOrNull("TB2") }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("findSelectedByBookIdOrNull") {
      case("selected") { dao.findSelectedByBookIdOrNull("B2")?.id }
      case("none") { dao.findSelectedByBookIdOrNull("B3") }
    }

    func("findAllBookIdsByThumbnailTypeAndDimensionSmallerThan") {
      case("generated smaller than 300") { dao.findAllBookIdsByThumbnailTypeAndDimensionSmallerThan(ThumbnailBook.Type.GENERATED, 300).sorted() }
      case("generated smaller than 401") { dao.findAllBookIdsByThumbnailTypeAndDimensionSmallerThan(ThumbnailBook.Type.GENERATED, 401).sorted() }
      case("sidecar") { dao.findAllBookIdsByThumbnailTypeAndDimensionSmallerThan(ThumbnailBook.Type.SIDECAR, 10000).sorted() }
      case("zero") { dao.findAllBookIdsByThumbnailTypeAndDimensionSmallerThan(ThumbnailBook.Type.USER_UPLOADED, 0) }
    }

    func("existsById") {
      case("existing") { listOf(dao.existsById("T1"), dao.existsById("TB4")) }
      case("missing") { dao.existsById("NOPE") }
    }

    func("getLibraryIdOrNull") {
      case("existing") { listOf(dao.getLibraryIdOrNull("T1"), dao.getLibraryIdOrNull("TB3")) }
      case("missing") { dao.getLibraryIdOrNull("NOPE") }
    }

    func("getSeriesIdOrNull") {
      case("existing") { listOf(dao.getSeriesIdOrNull("T2"), dao.getSeriesIdOrNull("TB3")) }
      case("missing") { dao.getSeriesIdOrNull("NOPE") }
    }

    func("toDomain") {
      case("stored values") { db.rawQuery("select ID, BOOK_ID, URL, SELECTED, TYPE, WIDTH, HEIGHT, FILE_SIZE, MEDIA_TYPE, length(THUMBNAIL) from THUMBNAIL_BOOK order by ID") }
      case("dates are not converted") { dao.findByIdOrNull("TB1")!!.let { listOf(it.createdDate, it.lastModifiedDate) } }
    }

    func("update") {
      case("all fields") {
        dao.update(th("T2", "B2", true, 5).copy(mediaType = "image/png"))
        stable(dao.findByIdOrNull("T2"))
      }
      case("move to other owner as sidecar") {
        dao.update(th("T2", "B3", false, 5, ThumbnailBook.Type.SIDECAR))
        listOf(ids("B2"), ids("B3"), dao.findByIdOrNull("T2")!!.url)
      }
      case("missing") {
        dao.update(th("NOPE"))
        dao.findByIdOrNull("NOPE")
      }
      case("unknown owner") { exceptionType { dao.update(th("T2", "NOPE")) } }
    }

    func("markSelected") {
      case("select other") {
        dao.markSelected(th("T3", "B2"))
        listOf(ids("B2"), ids("B1"))
      }
      case("select seeded") {
        dao.markSelected(th("TB2", "B1"))
        ids("B1")
      }
      case("missing thumbnail unselects all") {
        dao.markSelected(th("NOPE", "B6"))
        ids("B6")
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("T1")
        ids("B2")
      }
      case("missing") {
        dao.delete("NOPE")
        ids("B2")
      }
    }

    func("deleteByBookIdAndType") {
      case("matching type") {
        dao.deleteByBookIdAndType("B1", ThumbnailBook.Type.SIDECAR)
        ids("B1")
      }
      case("other type") {
        dao.deleteByBookIdAndType("B2", ThumbnailBook.Type.SIDECAR)
        ids("B2")
      }
    }

    func("deleteByBookId") {
      case("existing") {
        dao.deleteByBookId("B2")
        ids("B2")
      }
      case("missing") {
        dao.deleteByBookId("NOPE")
        ids("B6")
      }
    }

    func("deleteByBookIds") {
      case("empty") {
        dao.deleteByBookIds(emptyList())
        ids("B6")
      }
      case("some with large list") {
        dao.insert(th("T5", "B11"))
        dao.deleteByBookIds((1..1500).map { "X$it" } + listOf("B6", "B3"))
        listOf(ids("B6"), ids("B3"), ids("B11"), ids("B1"))
      }
    }
  }
}
