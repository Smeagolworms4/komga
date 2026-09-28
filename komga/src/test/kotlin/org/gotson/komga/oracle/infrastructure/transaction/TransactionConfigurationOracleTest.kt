package org.gotson.komga.oracle.infrastructure.transaction

import org.gotson.komga.domain.model.Library
import org.gotson.komga.infrastructure.transaction.TransactionConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.jdbc.support.JdbcTransactionManager
import java.net.URL

class TransactionConfigurationOracleTest : OracleTest() {
  private val db = OracleDb()
  private val template = TransactionConfiguration().transactionTemplate(JdbcTransactionManager(db.dataSource))

  private fun lib(id: String) = Library("lib $id", URL("file:/lib/$id"), id = id)

  override fun cases() {
    func("transactionTemplate") {
      case("returns the result") { template.execute { 42 } }
      case("commits") {
        template.execute { db.libraryDao.insert(lib("L1")) }
        db.libraryDao.count()
      }
      case("rolls back on exception") {
        val e =
          exceptionType {
            template.execute {
              db.libraryDao.insert(lib("L2"))
              throw IllegalStateException("boom")
            }
          }
        listOf(e, db.libraryDao.findAll().map { it.id })
      }
      case("exception is rethrown as is") { template.execute { throw IllegalArgumentException("inner") } }
      case("without result") {
        template.executeWithoutResult { db.libraryDao.insert(lib("L3")) }
        db.libraryDao.findAll().map { it.id }
      }
      case("sql error rolls back the whole block") {
        val e =
          exceptionType {
            template.executeWithoutResult {
              db.libraryDao.insert(lib("L4"))
              db.libraryDao.insert(lib("L1"))
            }
          }
        listOf(e, db.libraryDao.findAll().map { it.id })
      }
    }
  }
}
