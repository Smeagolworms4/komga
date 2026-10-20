package org.gotson.komga.oracle.infrastructure.datasource

import org.gotson.komga.infrastructure.datasource.FlywaySessionsMigrationInitializer
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.sqlite.SQLiteDataSource

class FlywaySessionsMigrationInitializerOracleTest : OracleTest() {
  private val dataSource =
    SingleConnectionDataSource(SQLiteDataSource().apply { url = "jdbc:sqlite::memory:" }.connection, true)

  private fun q(sql: String) = OracleDb.query(dataSource.connection, sql)

  private fun history() = q("select installed_rank, version, description, type, script, checksum, success from flyway_schema_history order by installed_rank")

  override fun cases() {
    func("afterPropertiesSet") {
      case("empty database") { q("select count(*) from sqlite_master") }
      case("migrates the sessions database") {
        FlywaySessionsMigrationInitializer(dataSource).afterPropertiesSet()
        q("select type, name, tbl_name from sqlite_master where name not like 'sqlite_%' order by type, name")
      }
      case("schema history") { history() }
      case("session table columns") { q("select name, type, \"notnull\", dflt_value, pk from pragma_table_info('SPRING_SESSION') order by cid") }
      case("session attributes table columns") { q("select name, type, \"notnull\", dflt_value, pk from pragma_table_info('SPRING_SESSION_ATTRIBUTES') order by cid") }
      case("idempotent") {
        FlywaySessionsMigrationInitializer(dataSource).afterPropertiesSet()
        history()
      }
      case("session tables usable") {
        OracleDb.exec(dataSource.connection, "insert into SPRING_SESSION(PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME) values ('p', 's', 1, 2, 3, 4, 'user')")
        OracleDb.exec(dataSource.connection, "insert into SPRING_SESSION_ATTRIBUTES(SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) values ('p', 'a', x'00')")
        q("select S.SESSION_ID, S.PRINCIPAL_NAME, SA.ATTRIBUTE_NAME, length(SA.ATTRIBUTE_BYTES) from SPRING_SESSION S join SPRING_SESSION_ATTRIBUTES SA on S.PRIMARY_ID = SA.SESSION_PRIMARY_ID")
      }
    }
  }
}
