package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.ThumbnailSeries
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL

class ThumbnailSeriesDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.thumbnailSeriesDao

  private fun th(
    id: String,
    owner: String = "S1",
    selected: Boolean = false,
    size: Int = 10,
    type: ThumbnailSeries.Type = ThumbnailSeries.Type.USER_UPLOADED,
  ) = ThumbnailSeries(
    thumbnail = if (type == ThumbnailSeries.Type.SIDECAR) null else oracleBytes(size),
    url = if (type == ThumbnailSeries.Type.SIDECAR) URL("file:/lib1/Batman/cover%20$id.jpg") else null,
    selected = selected,
    type = type,
    mediaType = "image/jpeg",
    fileSize = size.toLong(),
    dimension = Dimension(size, size * 2),
    id = id,
    seriesId = owner,
  )

  private fun ids(owner: String) = dao.findAllBySeriesId(owner).map { it.id to it.selected }

  override fun cases() {
    func("insert") {
      case("insert and read back") {
        NzDaoSeed.seed(db)
        dao.insert(th("T1", selected = true))
        stable(dao.findByIdOrNull("T1"))
      }
      case("sidecar with url") {
        dao.insert(th("T2", size = 3, type = ThumbnailSeries.Type.SIDECAR))
        stable(dao.findByIdOrNull("T2"))
      }
      case("other owner") {
        dao.insert(th("T3", "S3", true, 0))
        ids("S3")
      }
      case("duplicate id") { exceptionType { dao.insert(th("T1")) } }
      case("unknown owner") { exceptionType { dao.insert(th("T9", "NOPE")) } }
    }

    func("findAllBySeriesId") {
      case("all fields") { stable(dao.findAllBySeriesId("S1")) }
      case("none") { dao.findAllBySeriesId("S2") }
    }

    func("findAllBySeriesIdIdAndType") {
      case("sidecar") { dao.findAllBySeriesIdIdAndType("S1", ThumbnailSeries.Type.SIDECAR).map { it.id } }
      case("user uploaded") { dao.findAllBySeriesIdIdAndType("S1", ThumbnailSeries.Type.USER_UPLOADED).map { it.id } }
      case("none") { dao.findAllBySeriesIdIdAndType("S3", ThumbnailSeries.Type.SIDECAR) }
    }

    func("findByIdOrNull") {
      case("missing") { dao.findByIdOrNull("NOPE") }
    }

    func("getLibraryIdOrNull") {
      case("existing") { listOf(dao.getLibraryIdOrNull("T1"), dao.getLibraryIdOrNull("T3")) }
      case("missing") { dao.getLibraryIdOrNull("NOPE") }
    }

    func("getSeriesIdOrNull") {
      case("existing") { listOf(dao.getSeriesIdOrNull("T2"), dao.getSeriesIdOrNull("T3")) }
      case("missing") { dao.getSeriesIdOrNull("NOPE") }
    }

    func("findSelectedBySeriesIdOrNull") {
      case("selected") { dao.findSelectedBySeriesIdOrNull("S1")?.id }
      case("none") { dao.findSelectedBySeriesIdOrNull("S2") }
    }

    func("toDomain") {
      case("stored values") { db.rawQuery("select ID, SERIES_ID, URL, SELECTED, TYPE, WIDTH, HEIGHT, FILE_SIZE, MEDIA_TYPE, length(THUMBNAIL) from THUMBNAIL_SERIES order by ID") }
      case("url") { dao.findByIdOrNull("T2")!!.url }
    }

    func("update") {
      case("all fields") {
        dao.update(th("T2", "S1", true, 5).copy(mediaType = "image/png"))
        stable(dao.findByIdOrNull("T2"))
      }
      case("move to other owner and back to sidecar") {
        dao.update(th("T2", "S3", false, 5, ThumbnailSeries.Type.SIDECAR))
        listOf(ids("S1"), ids("S3"), dao.findByIdOrNull("T2")!!.url)
      }
      case("missing") {
        dao.update(th("NOPE"))
        dao.findByIdOrNull("NOPE")
      }
      case("unknown owner") { exceptionType { dao.update(th("T2", "NOPE")) } }
    }

    func("markSelected") {
      case("select other") {
        dao.insert(th("T4", "S1", false))
        dao.markSelected(th("T4", "S1"))
        listOf(ids("S1"), ids("S3"))
      }
      case("select in other owner") {
        dao.markSelected(th("T2", "S3"))
        listOf(ids("S1"), ids("S3"))
      }
      case("missing thumbnail unselects all") {
        dao.markSelected(th("NOPE", "S3"))
        ids("S3")
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("T1")
        ids("S1")
      }
      case("missing") {
        dao.delete("NOPE")
        ids("S1")
      }
    }

    func("deleteBySeriesId") {
      case("existing") {
        dao.deleteBySeriesId("S1")
        ids("S1")
      }
      case("missing") {
        dao.deleteBySeriesId("NOPE")
        ids("S3")
      }
    }

    func("deleteBySeriesIds") {
      case("empty") {
        dao.deleteBySeriesIds(emptyList())
        ids("S3")
      }
      case("some with large list") {
        dao.insert(th("T5", "S6"))
        dao.insert(th("T6", "S2"))
        dao.deleteBySeriesIds((1..1500).map { "X$it" } + listOf("S3", "S6"))
        listOf(ids("S3"), ids("S6"), ids("S2"))
      }
    }
  }
}
