package org.gotson.komga.oracle.infrastructure.datasource

import org.gotson.komga.infrastructure.datasource.FlywaySecondaryMigrationInitializer
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.sqlite.SQLiteDataSource

class FlywaySecondaryMigrationInitializerOracleTest : OracleTest() {
  private val dataSource =
    SingleConnectionDataSource(SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }.connection, true)

  private fun q(sql: String) = OracleDb.query(dataSource.connection, sql)

  private fun history() = q("select installed_rank, version, description, type, script, checksum, success from flyway_schema_history order by installed_rank")

  override fun cases() {
    func("afterPropertiesSet") {
      case("empty database") { q("select count(*) from sqlite_master") }
      case("migrates the tasks database") {
        FlywaySecondaryMigrationInitializer(dataSource).afterPropertiesSet()
        q("select type, name, tbl_name from sqlite_master where name not like 'sqlite_%' order by type, name")
      }
      case("schema history") { history() }
      case("task table columns") { q("select name, type, \"notnull\", dflt_value, pk from pragma_table_info('TASK') order by cid") }
      case("idempotent") {
        FlywaySecondaryMigrationInitializer(dataSource).afterPropertiesSet()
        history()
      }
      case("task table usable") {
        OracleDb.exec(dataSource.connection, "insert into TASK(ID, PRIORITY, CLASS, SIMPLE_TYPE, PAYLOAD) values ('t', 4, 'c', 's', '{}')")
        q("select ID, PRIORITY, GROUP_ID, OWNER, length(CREATED_DATE) from TASK")
      }
    }
  }
}
