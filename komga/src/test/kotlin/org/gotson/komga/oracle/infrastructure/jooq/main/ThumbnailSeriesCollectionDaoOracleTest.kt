package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailSeriesCollection
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class ThumbnailSeriesCollectionDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.thumbnailSeriesCollectionDao

  private fun th(
    id: String,
    owner: String = "C1",
    selected: Boolean = false,
    size: Int = 10,
  ) = ThumbnailSeriesCollection(oracleBytes(size), selected, ThumbnailSeriesCollection.Type.USER_UPLOADED, "image/jpeg", size.toLong(), Dimension(size, size * 2), id, owner)

  private fun ids(owner: String) = dao.findAllByCollectionId(owner).map { it.id to it.selected }

  override fun cases() {
    func("insert") {
      case("insert and read back") {
        NzDaoSeed.seed(db)
        dao.insert(th("T1", selected = true))
        stable(dao.findByIdOrNull("T1"))
      }
      case("second thumbnail") {
        dao.insert(th("T2", size = 3))
        ids("C1")
      }
      case("other owner") {
        dao.insert(th("T3", "C2", true, 0))
        ids("C2")
      }
      case("duplicate id") { exceptionType { dao.insert(th("T1")) } }
      case("unknown owner") { exceptionType { dao.insert(th("T9", "NOPE")) } }
    }

    func("findAllByCollectionId") {
      case("all fields") { stable(dao.findAllByCollectionId("C1")) }
      case("none") { dao.findAllByCollectionId("C3") }
      case("unknown") { dao.findAllByCollectionId("NOPE") }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("T3")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("findSelectedByCollectionIdOrNull") {
      case("selected") { dao.findSelectedByCollectionIdOrNull("C1")?.id }
      case("none") { dao.findSelectedByCollectionIdOrNull("C3") }
    }

    func("toDomain") {
      case("stored values") { db.rawQuery("select ID, SELECTED, TYPE, WIDTH, HEIGHT, FILE_SIZE, MEDIA_TYPE, length(THUMBNAIL) from THUMBNAIL_COLLECTION order by ID") }
      case("thumbnail bytes and dimension") { dao.findByIdOrNull("T2")!!.let { listOf(it.thumbnail, it.dimension, it.fileSize, it.type) } }
    }

    func("update") {
      case("all fields") {
        dao.update(th("T2", "C1", true, 5).copy(mediaType = "image/png"))
        stable(dao.findByIdOrNull("T2"))
      }
      case("move to other owner") {
        dao.update(th("T2", "C2", false, 5))
        listOf(ids("C1"), ids("C2"))
      }
      case("missing") {
        dao.update(th("NOPE"))
        dao.findByIdOrNull("NOPE")
      }
      case("unknown owner") { exceptionType { dao.update(th("T2", "NOPE")) } }
    }

    func("markSelected") {
      case("select other") {
        dao.insert(th("T4", "C1", false))
        dao.markSelected(th("T4", "C1"))
        listOf(ids("C1"), ids("C2"))
      }
      case("select in other owner") {
        dao.markSelected(th("T2", "C2"))
        listOf(ids("C1"), ids("C2"))
      }
      case("missing thumbnail unselects all") {
        dao.markSelected(th("NOPE", "C2"))
        ids("C2")
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("T1")
        ids("C1")
      }
      case("missing") {
        dao.delete("NOPE")
        ids("C1")
      }
    }

    func("deleteByCollectionId") {
      case("existing") {
        dao.deleteByCollectionId("C1")
        ids("C1")
      }
      case("missing") {
        dao.deleteByCollectionId("NOPE")
        ids("C2")
      }
    }

    func("deleteByCollectionIds") {
      case("empty") {
        dao.deleteByCollectionIds(emptyList())
        ids("C2")
      }
      case("some") {
        dao.insert(th("T5", "C3"))
        dao.deleteByCollectionIds(listOf("C2", "NOPE"))
        listOf(ids("C2"), ids("C3"))
      }
    }
  }
}
