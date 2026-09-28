package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Library
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import java.net.URL
import java.time.LocalDateTime

class LibraryDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.libraryDao

  private fun lib(
    id: String,
    name: String = "lib $id",
  ) = Library(name = name, root = URL("file:/libraries/$id"), id = id)

  private val full =
    Library(
      name = "Bibliothèque ünïcode 漫画",
      root = URL("file:/data/My%20Comics/"),
      importComicInfoBook = false,
      importComicInfoSeries = false,
      importComicInfoCollection = false,
      importComicInfoReadList = false,
      importComicInfoSeriesAppendVolume = false,
      importEpubBook = false,
      importEpubSeries = false,
      importMylarSeries = false,
      importLocalArtwork = false,
      importBarcodeIsbn = false,
      scanForceModifiedTime = true,
      scanOnStartup = true,
      scanInterval = Library.ScanInterval.WEEKLY,
      scanCbx = false,
      scanPdf = false,
      scanEpub = false,
      scanDirectoryExclusions = setOf("#recycle", "@eaDir", "", "ünï"),
      repairExtensions = true,
      convertToCbz = true,
      emptyTrashAfterScan = true,
      seriesCover = Library.SeriesCover.LAST,
      hashFiles = false,
      hashPages = true,
      hashKoreader = true,
      analyzeDimensions = false,
      oneshotsDirectory = "_oneshots",
      unavailableDate = LocalDateTime.of(2021, 3, 28, 2, 30, 15, 123456789),
      id = "FULL",
      createdDate = LocalDateTime.of(2020, 1, 1, 0, 0),
    )

  override fun cases() {
    func("count") {
      case("empty database") { dao.count() }
    }

    func("findAll") {
      case("empty database") { dao.findAll() }
    }

    func("insert") {
      case("defaults") {
        dao.insert(lib("L1"))
        stable(dao.findById("L1"))
      }
      case("all fields set") {
        dao.insert(full)
        stable(dao.findById("FULL"))
      }
      case("duplicate id") { exceptionType { dao.insert(lib("L1")) } }
      case("duplicate root is allowed") {
        dao.insert(lib("L2").copy(root = URL("file:/libraries/L1")))
        dao.count()
      }
      case("tsid id") {
        dao.insert(Library(name = "generated", root = URL("file:/gen"), id = "0ABCDEFGHJKMN"))
        stable(dao.findByIdOrNull("0ABCDEFGHJKMN"))
      }
    }

    func("insertDirectoryExclusions") {
      case("no exclusion") { dao.findById("L1").scanDirectoryExclusions }
      case("several exclusions") { dao.findById("FULL").scanDirectoryExclusions }
    }

    func("findByIdOrNull") {
      case("existing") { stable(dao.findByIdOrNull("L2")) }
      case("missing") { dao.findByIdOrNull("NOPE") }
      case("empty id") { dao.findByIdOrNull("") }
      case("case sensitive") { dao.findByIdOrNull("l1") }
    }

    func("findById") {
      case("existing") { stable(dao.findById("L1")) }
      case("missing") { dao.findById("NOPE") }
    }

    func("findOne") {
      case("with exclusions") { dao.findById("FULL").scanDirectoryExclusions.size }
    }

    func("findAll") {
      case("all libraries") { stable(dao.findAll()) }
    }

    func("findAllByIds") {
      case("empty") { dao.findAllByIds(emptyList()) }
      case("some") { stable(dao.findAllByIds(listOf("L2", "L1", "NOPE"))) }
      case("duplicates") { dao.findAllByIds(listOf("L1", "L1")).map { it.id } }
      case("missing only") { dao.findAllByIds(setOf("X", "Y")) }
    }

    func("selectBase") {
      case("library without exclusion is returned once") { dao.findAll().map { it.id } }
    }

    func("fetchAndMap") {
      case("groups exclusions per library") { dao.findAll().map { it.id to it.scanDirectoryExclusions } }
    }

    func("toDomain") {
      case("dates in current time zone") {
        val l = dao.findById("FULL")
        stable(listOf(l.createdDate, l.unavailableDate))
      }
      case("path") { dao.findById("FULL").path }
      case("stored values") { db.rawQuery("select UNAVAILABLE_DATE, SCAN_INTERVAL, HASH_FILES, ROOT from LIBRARY where ID = 'FULL'") }
    }

    func("update") {
      case("all fields") {
        dao.update(
          full.copy(
            name = "renamed",
            root = URL("file:/other"),
            scanDirectoryExclusions = setOf("new"),
            scanInterval = Library.ScanInterval.DISABLED,
            seriesCover = Library.SeriesCover.FIRST_UNREAD_OR_LAST,
            oneshotsDirectory = null,
            unavailableDate = null,
          ),
        )
        stable(dao.findById("FULL"))
      }
      case("remove exclusions") {
        dao.update(dao.findById("FULL").copy(scanDirectoryExclusions = emptySet()))
        dao.findById("FULL").scanDirectoryExclusions
      }
      case("missing library") {
        dao.update(lib("NOPE"))
        dao.findByIdOrNull("NOPE")
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("L2")
        dao.findAll().map { it.id }
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("deleteAll") {
      case("all") {
        dao.deleteAll()
        listOf(dao.count(), dao.findAll())
      }
      case("already empty") {
        dao.deleteAll()
        dao.count()
      }
    }
  }
}
