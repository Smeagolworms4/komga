package org.gotson.komga.oracle.infrastructure.jooq.tasks

import org.gotson.komga.application.tasks.Task
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.CopyMode
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class TasksDaoOracleTest : OracleTest() {
  private val db = OracleDb()
  private val dao = db.tasksDao

  private fun q(sql: String) = OracleDb.query(db.tasksDataSource.connection, sql)

  private fun exec(vararg sql: String) = OracleDb.exec(db.tasksDataSource.connection, *sql)

  private fun rows() = q("select ID, PRIORITY, GROUP_ID, CLASS, SIMPLE_TYPE, PAYLOAD, OWNER from TASK order by ID")

  private fun owners() = q("select ID, OWNER from TASK order by ID")

  /** fixed modification dates, in the order of the ids given, so that the order of takeFirst does not depend on timing */
  private fun dated(vararg ids: String) = ids.forEachIndexed { i, id -> exec("update TASK set LAST_MODIFIED_DATE = '2020-01-01 00:00:0$i' where ID = '$id'") }

  private fun str(t: Task?) = t?.toString()

  override fun cases() {
    func("count") {
      case("empty") { dao.count() }
    }
    func("hasAvailable") {
      case("empty") { dao.hasAvailable() }
    }
    func("findAll") {
      case("empty") { dao.findAll() }
    }
    func("findAllGroupedByOwner") {
      case("empty") { dao.findAllGroupedByOwner() }
    }
    func("countBySimpleType") {
      case("empty") { dao.countBySimpleType() }
    }
    func("takeFirst") {
      case("empty") { dao.takeFirst("w1") }
    }

    func("save@118") {
      case("one task") {
        dao.save(Task.ScanLibrary("L1", true, 6))
        rows()
      }
      case("dates set") {
        q("select CREATED_DATE = LAST_MODIFIED_DATE, abs(julianday(CREATED_DATE) - julianday('now')) < 0.01 from TASK")
      }
      case("same task again updates it") {
        dao.save(Task.ScanLibrary("L1", true, 2))
        rows()
      }
      case("update sets the modification date") {
        q("select LAST_MODIFIED_DATE >= CREATED_DATE, abs(julianday(LAST_MODIFIED_DATE) - julianday('now')) < 0.01, LAST_MODIFIED_DATE like '____-__-__ __:__:__%' from TASK")
      }
      case("task with group") {
        dao.save(Task.AnalyzeBook("B1", 4, "S1"))
        rows()
      }
      case("unique id shared by different parameters") {
        dao.save(Task.RebuildIndex(setOf(LuceneEntity.Book)))
        dao.save(Task.RebuildIndex(null, 8))
        q("select ID, PRIORITY, PAYLOAD from TASK where ID = 'REBUILD_INDEX'")
      }
    }

    func("save@122") {
      case("several tasks") {
        dao.save(
          listOf(
            Task.AnalyzeBook("B2", 4, "S1"),
            Task.AnalyzeBook("B3", 4, "S2"),
            Task.HashBook("B1", 0),
            Task.RefreshBookMetadata("B1", setOf(BookMetadataPatchCapability.TITLE), 5, "S1"),
            Task.ImportBook("/a.cbz", "S3", CopyMode.MOVE, null, null, 6),
          ),
        )
        rows()
      }
      case("duplicates in the same batch") {
        dao.save(listOf(Task.DeleteBook("B9", 1), Task.DeleteBook("B9", 3)))
        q("select ID, PRIORITY from TASK where ID = 'DELETE_BOOK_B9'")
      }
      case("empty collection") {
        dao.save(emptyList())
        dao.count()
      }
      case("more than a batch") {
        dao.save((1..2500).map { Task.GenerateBookThumbnail("T$it", 1) })
        val c = dao.count()
        exec("delete from TASK where ID like 'GENERATE_BOOK_THUMBNAIL_T%'")
        c
      }
    }

    func("toQuery") {
      case("class and simple type") { q("select distinct CLASS, SIMPLE_TYPE from TASK order by CLASS") }
      case("payloads") { q("select PAYLOAD from TASK order by ID") }
    }

    func("count") {
      case("some tasks") { dao.count() }
    }

    func("countBySimpleType") {
      case("some tasks") { dao.countBySimpleType() }
    }

    func("findAll") {
      case("all tasks") { dao.findAll().map { it.toString() } }
      case("types") { dao.findAll().map { it.javaClass.simpleName } }
    }

    func("selectBase") {
      case("class and payload are read") { dao.findAll().map { it.uniqueId } }
    }

    func("toDomain") {
      case("unknown class is skipped") {
        exec("insert into TASK(ID, PRIORITY, CLASS, SIMPLE_TYPE, PAYLOAD) values ('BAD_CLASS', 1, 'org.gotson.komga.application.tasks.Task${'$'}Nope', 'Nope', '{}')")
        dao.findAll().size
      }
      case("invalid payload is skipped") {
        exec("insert into TASK(ID, PRIORITY, CLASS, SIMPLE_TYPE, PAYLOAD) values ('BAD_PAYLOAD', 1, 'org.gotson.komga.application.tasks.Task${'$'}HashBook', 'HashBook', '{\"nope\":1}')")
        dao.findAll().size
      }
      case("payload read as is") {
        exec(
          "insert into TASK(ID, PRIORITY, CLASS, SIMPLE_TYPE, PAYLOAD) values ('ODD', 1, 'org.gotson.komga.application.tasks.Task${'$'}DeleteSeries', 'x', '{\"seriesId\":\"S7\",\"priority\":9,\"groupId\":\"G\",\"uniqueId\":\"U\"}')",
        )
        dao.findAll().filter { it is Task.DeleteSeries }.map { listOf(it.toString(), it.uniqueId, it.groupId) }
      }
      case("cleanup") {
        dao.delete("BAD_CLASS")
        dao.delete("BAD_PAYLOAD")
        dao.delete("ODD")
        dao.count()
      }
    }

    func("hasAvailable") {
      case("some tasks") { dao.hasAvailable() }
    }

    func("takeFirst") {
      case("highest priority first") {
        dated("SCAN_LIBRARY_L1_DEEP_true", "ANALYZE_BOOK_B1", "ANALYZE_BOOK_B2", "ANALYZE_BOOK_B3", "HASH_BOOK_B1", "REFRESH_BOOK_METADATA_B1", "IMPORT_BOOK_S3_/a.cbz", "REBUILD_INDEX", "DELETE_BOOK_B9")
        str(dao.takeFirst("w1"))
      }
      case("owner set") { owners() }
      case("next one") { str(dao.takeFirst("w2")) }
      case("group of an owned task is skipped") { str(dao.takeFirst("w3")) }
      case("owners") { owners() }
      case("available") { dao.hasAvailable() }
      case("until empty") { (1..10).map { str(dao.takeFirst("w4")) } }
      case("none available") { dao.hasAvailable() }
      case("grouped by owner") { dao.findAllGroupedByOwner().mapValues { (_, v) -> v.map { it.toString() } } }
    }

    func("findAllGroupedByOwner") {
      case("keys") { dao.findAllGroupedByOwner().keys }
    }

    func("disown") {
      case("resets owners") { dao.disown() }
      case("owners") { owners() }
      case("nothing to disown") { dao.disown() }
      case("available again") { dao.hasAvailable() }
    }

    func("takeFirst") {
      case("same priority, oldest first") {
        exec("update TASK set PRIORITY = 1")
        dated("DELETE_BOOK_B9", "HASH_BOOK_B1", "ANALYZE_BOOK_B3")
        str(dao.takeFirst("x"))
      }
      case("undeserializable first task") {
        exec("insert into TASK(ID, PRIORITY, CLASS, SIMPLE_TYPE, PAYLOAD) values ('BAD', 99, 'nope', 'nope', '{}')")
        listOf(str(dao.takeFirst("y")), q("select OWNER from TASK where ID = 'BAD'"))
      }
      case("group of a task owned by a bad row") {
        exec("update TASK set OWNER = 'z', GROUP_ID = 'S1' where ID = 'BAD'")
        str(dao.takeFirst("y"))
      }
    }

    func("delete") {
      case("existing") {
        dao.delete("BAD")
        dao.count()
      }
      case("missing") {
        dao.delete("NOPE")
        dao.count()
      }
    }

    func("deleteAllWithoutOwner") {
      case("deletes tasks without owner") { dao.deleteAllWithoutOwner() }
      case("remaining") { owners() }
      case("again") { dao.deleteAllWithoutOwner() }
    }

    func("deleteAll") {
      case("deletes everything") {
        dao.save(Task.UpgradeIndex())
        dao.deleteAll()
        dao.count()
      }
      case("empty") {
        dao.deleteAll()
        listOf(dao.count(), dao.hasAvailable(), dao.takeFirst("w"))
      }
    }
  }
}
