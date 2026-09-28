package org.gotson.komga.oracle.infrastructure.jooq

import org.gotson.komga.infrastructure.jooq.KomgaJooqConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.gotson.komga.oracle.infrastructure.jooq.JooqSamples.render
import org.jooq.DSLContext
import org.jooq.ExecuteListenerProvider
import org.jooq.TransactionProvider
import org.jooq.impl.DSL
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource

class KomgaJooqConfigurationOracleTest : OracleTest() {
  private val beans = DefaultListableBeanFactory()
  private val transactionProvider = beans.getBeanProvider(TransactionProvider::class.java)
  private val listeners = beans.getBeanProvider(ExecuteListenerProvider::class.java)

  private fun dataSource() = SingleConnectionDataSource(SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }.connection, true)

  /** a query rendered and run, a table created through the context and seen through the data source */
  private fun check(
    ds: DataSource,
    dsl: DSLContext,
  ): List<Any?> {
    dsl.execute("create table T (A varchar, B int)")
    dsl.insertInto(DSL.table(DSL.name("T")), DSL.field(DSL.name("A")), DSL.field(DSL.name("B"))).values("x", 1).execute()
    return listOf(
      render(dsl.selectOne()),
      dsl.selectOne().fetchOne()?.value1(),
      OracleDb.query(ds.connection, "select A, B from T"),
      dsl.fetchCount(DSL.table(DSL.name("T"))),
    )
  }

  override fun cases() {
    val conf = KomgaJooqConfiguration()
    func("mainDslContextRW") {
      case("context") { dataSource().let { check(it, conf.mainDslContextRW(it, transactionProvider, listeners)) } }
    }
    func("mainDslContextRO") {
      case("context") { dataSource().let { check(it, conf.mainDslContextRO(it, transactionProvider, listeners)) } }
    }
    func("tasksDslContextRW") {
      case("context") { dataSource().let { check(it, conf.tasksDslContextRW(it, transactionProvider, listeners)) } }
    }
    func("tasksDslContextRO") {
      case("context") { dataSource().let { check(it, conf.tasksDslContextRO(it, transactionProvider, listeners)) } }
    }
    func("createDslContext") {
      case("sqlite rendering") {
        render(
          conf
            .mainDslContextRW(dataSource(), transactionProvider, listeners)
            .select(DSL.field(DSL.name("A")))
            .from(DSL.table(DSL.name("T")))
            .where(DSL.field(DSL.name("B"), Int::class.java).gt(1))
            .limit(2)
            .offset(3),
        )
      }
      case("sql error") {
        exceptionType { conf.mainDslContextRW(dataSource(), transactionProvider, listeners).execute("select * from NOPE") } != null
      }
    }
  }
}
