package org.gotson.komga.oracle.infrastructure.jooq.main

import org.gotson.komga.domain.model.Book
import org.gotson.komga.domain.model.KomgaUser
import org.gotson.komga.domain.model.Library
import org.gotson.komga.domain.model.Series
import org.gotson.komga.oracle.OracleDb
import java.net.URL
import java.time.LocalDateTime

/** Fixed test data for the DAO oracles (mirrored by test/unit/infrastructure/jooq/main/seed.ts in KomgaJS) */
object DaoSeed {
  val T0: LocalDateTime = LocalDateTime.of(2020, 1, 1, 10, 0)

  fun library(id: String) = Library(name = "lib $id", root = URL("file:/libraries/$id"), id = id, createdDate = T0)

  fun series(
    id: String,
    libraryId: String,
    name: String = "series $id",
  ) = Series(name = name, url = URL("file:/libraries/$libraryId/$id"), fileLastModified = T0, id = id, libraryId = libraryId, createdDate = T0)

  fun book(
    id: String,
    seriesId: String,
    libraryId: String,
    name: String = "book $id",
    number: Int = 0,
    fileSize: Long = 0,
    ext: String = "cbz",
  ) = Book(
    name = name,
    url = URL("file:/libraries/$libraryId/$seriesId/$id.$ext"),
    fileLastModified = T0,
    fileSize = fileSize,
    number = number,
    id = id,
    seriesId = seriesId,
    libraryId = libraryId,
    createdDate = T0,
  )

  fun user(
    id: String,
    email: String = "$id@example.org",
  ) = KomgaUser(email = email, password = "secret", id = id, createdDate = T0)

  /**
   * Runs [block] in a transaction rolled back on failure, like the `@Transactional` DAO method it calls
   * (the KomgaJS DAOs apply `@Transactional` themselves: the TypeScript twin calls the method directly)
   */
  fun <T> transactional(
    db: OracleDb,
    block: () -> T,
  ): T {
    val c = db.dataSource.connection
    c.autoCommit = false
    try {
      return block().also { c.commit() }
    } catch (e: Throwable) {
      c.rollback()
      throw e
    } finally {
      c.autoCommit = true
    }
  }

  /** Executes raw SQL statements on the main database */
  fun sql(
    db: OracleDb,
    vararg statements: String,
  ) = OracleDb.exec(db.dataSource.connection, *statements)
}
