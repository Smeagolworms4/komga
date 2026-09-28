package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailReadList
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class ThumbnailReadListDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.thumbnailReadListDao

  private fun th(
    id: String,
    owner: String = "RL1",
    selected: Boolean = false,
    size: Int = 10,
  ) = ThumbnailReadList(oracleBytes(size), selected, ThumbnailReadList.Type.USER_UPLOADED, "image/jpeg", size.toLong(), Dimension(size, size * 2), id, owner)

  private fun ids(owner: String) = dao.findAllByReadListId(owner).map { it.id to it.selected }

  override fun cases() {
    func("insert") {
      case("insert and read back") {
        NzDaoSeed.seed(db)
        dao.insert(th("T1", selected = true))
        stable(dao.findByIdOrNull("T1"))
      }
      case("second thumbnail") {
        dao.insert(th("T2", size = 3))
        ids("RL1")
      }
      case("other owner") {
        dao.insert(th("T3", "RL2", true, 0))
        ids("RL2")
      }
      case("duplicate id") { exceptionType { dao.insert(th("T1")) } }
      case("unknown owner") { exceptionType { dao.insert(th("T9", "NOPE")) } }
    }

    func("findAllByReadListId") {
      case("all fields") { stable(dao.findAllByReadListId("RL1")) }
      case("none") { dao.findAllByReadListId("RL3") }
      case("unknown") { dao.findAllByReadListId("NOPE") }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("T3")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("findSelectedByReadListIdOrNull") {
      case("selected") { dao.findSelectedByReadListIdOrNull("RL1")?.id }
      case("none") { dao.findSelectedByReadListIdOrNull("RL3") }
    }

    func("toDomain") {
      case("stored values") { db.rawQuery("select ID, SELECTED, TYPE, WIDTH, HEIGHT, FILE_SIZE, MEDIA_TYPE, length(THUMBNAIL) from THUMBNAIL_READLIST order by ID") }
      case("thumbnail bytes and dimension") { dao.findByIdOrNull("T2")!!.let { listOf(it.thumbnail, it.dimension, it.fileSize, it.type) } }
    }

    func("update") {
      case("all fields") {
        dao.update(th("T2", "RL1", true, 5).copy(mediaType = "image/png"))
        stable(dao.findByIdOrNull("T2"))
      }
      case("move to other owner") {
        dao.update(th("T2", "RL2", false, 5))
        listOf(ids("RL1"), ids("RL2"))
      }
      case("missing") {
        dao.update(th("NOPE"))
        dao.findByIdOrNull("NOPE")
      }
      case("unknown owner") { exceptionType { dao.update(th("T2", "NOPE")) } }
    }

    func("markSelected") {
      case("select other") {
        dao.insert(th("T4", "RL1", false))
        dao.markSelected(th("T4", "RL1"))
        listOf(ids("RL1"), ids("RL2"))
      }
      case("select in other owner") {
        dao.markSelected(th("T2", "RL2"))
        listOf(ids("RL1"), ids("RL2"))
      }
      case("missing thumbnail unselects all") {
        dao.markSelected(th("NOPE", "RL2"))
        ids("RL2")
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("T1")
        ids("RL1")
      }
      case("missing") {
        dao.delete("NOPE")
        ids("RL1")
      }
    }

    func("deleteByReadListId") {
      case("existing") {
        dao.deleteByReadListId("RL1")
        ids("RL1")
      }
      case("missing") {
        dao.deleteByReadListId("NOPE")
        ids("RL2")
      }
    }

    func("deleteByReadListIds") {
      case("empty") {
        dao.deleteByReadListIds(emptyList())
        ids("RL2")
      }
      case("some") {
        dao.insert(th("T5", "RL3"))
        dao.deleteByReadListIds(listOf("RL2", "NOPE"))
        listOf(ids("RL2"), ids("RL3"))
      }
    }
  }
}
