package org.gotson.komga.oracle.infrastructure.datasource

import com.zaxxer.hikari.HikariDataSource
import org.gotson.komga.infrastructure.configuration.KomgaProperties
import org.gotson.komga.infrastructure.datasource.DataSourcesConfiguration
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest
import org.sqlite.SQLiteConfig.JournalMode
import org.sqlite.SQLiteDataSource
import java.time.Duration
import javax.sql.DataSource

class DataSourcesConfigurationOracleTest : OracleTest() {
  private fun props(
    file: String,
    block: KomgaProperties.Database.() -> Unit = {},
  ) = KomgaProperties().apply {
    database.file = file
    database.block()
    tasksDb.file = file
    tasksDb.block()
  }

  private fun file(name: String) = tempDir.resolve(name).toString()

  /** pool name and size, data source class, url, journal mode, busy timeout, foreign keys */
  private fun describe(ds: DataSource): List<Any?> {
    val h = ds as HikariDataSource
    val s = h.dataSource as SQLiteDataSource
    val p = s.config.toProperties()
    return listOf(
      h.poolName,
      h.maximumPoolSize,
      s::class.java.simpleName,
      s.url.replace(tempDir.toString(), "<tmp>"),
      p.getProperty("journal_mode"),
      s.config.busyTimeout,
      p.getProperty("foreign_keys") == "true",
    ).also { h.close() }
  }

  /** pragmas of a connection of the pool */
  private fun pragmas(ds: DataSource): List<Any?> =
    (ds as HikariDataSource).use { h ->
      h.connection.use { c ->
        listOf("journal_mode", "foreign_keys", "busy_timeout", "cache_size", "temp_store").map { OracleDb.query(c, "pragma $it").single().single() }
      }
    }

  override fun cases() {
    func("sqliteDataSourceRW") {
      case("memory") { describe(DataSourcesConfiguration(props(":memory:")).sqliteDataSourceRW()) }
      case("memory pragmas") { pragmas(DataSourcesConfiguration(props(":memory:")).sqliteDataSourceRW()) }
      case("memory mode url") { describe(DataSourcesConfiguration(props("file:komga?mode=memory&cache=shared")).sqliteDataSourceRW()) }
      case("file with WAL") { describe(DataSourcesConfiguration(props(file("wal.sqlite")) { poolSize = 4 }).sqliteDataSourceRW()) }
      case("file with WAL pragmas") { pragmas(DataSourcesConfiguration(props(file("wal.sqlite"))).sqliteDataSourceRW()) }
      case("file with DELETE journal and pool size") {
        describe(
          DataSourcesConfiguration(
            props(file("delete.sqlite")) {
              journalMode = JournalMode.DELETE
              poolSize = 3
            },
          ).sqliteDataSourceRW(),
        )
      }
      case("file with DELETE journal pragmas") { pragmas(DataSourcesConfiguration(props(file("delete.sqlite")) { journalMode = JournalMode.DELETE }).sqliteDataSourceRW()) }
      case("file without journal mode") { describe(DataSourcesConfiguration(props(file("none.sqlite")) { journalMode = null }).sqliteDataSourceRW()) }
      case("pragmas in url") {
        describe(DataSourcesConfiguration(props(":memory:") { pragmas = linkedMapOf("cache_size" to "-4000", "temp_store" to "memory") }).sqliteDataSourceRW())
      }
      case("pragmas applied") {
        pragmas(DataSourcesConfiguration(props(":memory:") { pragmas = linkedMapOf("cache_size" to "-4000", "temp_store" to "memory") }).sqliteDataSourceRW())
      }
      case("busy timeout in seconds") { describe(DataSourcesConfiguration(props(":memory:") { busyTimeout = Duration.ofSeconds(7) }).sqliteDataSourceRW()) }
      case("busy timeout in millis") { describe(DataSourcesConfiguration(props(":memory:") { busyTimeout = Duration.ofMillis(1500) }).sqliteDataSourceRW()) }
      case("busy timeout pragma") { pragmas(DataSourcesConfiguration(props(":memory:") { busyTimeout = Duration.ofSeconds(2) }).sqliteDataSourceRW()) }
    }

    func("sqliteDataSourceRO") {
      case("memory uses the RW configuration") { describe(DataSourcesConfiguration(props(":memory:")).sqliteDataSourceRO()) }
      case("file with WAL") { describe(DataSourcesConfiguration(props(file("wal.sqlite")) { poolSize = 4 }).sqliteDataSourceRO()) }
      case("file with DELETE journal") { describe(DataSourcesConfiguration(props(file("delete.sqlite")) { journalMode = JournalMode.DELETE }).sqliteDataSourceRO()) }
    }

    func("tasksDataSourceRW") {
      case("memory") { describe(DataSourcesConfiguration(props(":memory:")).tasksDataSourceRW()) }
      case("file with pool size") { describe(DataSourcesConfiguration(props(file("tasks.sqlite")) { poolSize = 5 }).tasksDataSourceRW()) }
      case("memory pragmas") { pragmas(DataSourcesConfiguration(props(":memory:")).tasksDataSourceRW()) }
    }

    func("tasksDataSourceRO") {
      case("memory uses the RW configuration") { describe(DataSourcesConfiguration(props(":memory:")).tasksDataSourceRO()) }
      case("file with WAL") { describe(DataSourcesConfiguration(props(file("tasks.sqlite")) { poolSize = 2 }).tasksDataSourceRO()) }
      case("file with TRUNCATE journal") { describe(DataSourcesConfiguration(props(file("tasks.sqlite")) { journalMode = JournalMode.TRUNCATE }).tasksDataSourceRO()) }
    }

    func("buildDataSource") {
      case("main and tasks classes") {
        val c = DataSourcesConfiguration(props(":memory:"))
        listOf(describe(c.sqliteDataSourceRW())[2], describe(c.tasksDataSourceRW())[2])
      }
      case("max pool size bounds the default") { describe(DataSourcesConfiguration(props(file("m.sqlite")) { journalMode = JournalMode.DELETE }).sqliteDataSourceRW())[1] }
      case("memory ignores pool size") { describe(DataSourcesConfiguration(props(":memory:") { poolSize = 8 }).sqliteDataSourceRW())[1] }
      case("empty pragmas") { describe(DataSourcesConfiguration(props(file("x.sqlite")) { pragmas = emptyMap() }).sqliteDataSourceRW())[3] }
      case("single pragma") { describe(DataSourcesConfiguration(props(file("x.sqlite")) { pragmas = mapOf("a" to "b") }).sqliteDataSourceRW())[3] }
    }

    func("isMemory") {
      val files = listOf(":memory:", "file::memory:?cache=shared", "file:x?mode=memory", "/data/database.sqlite", "", "MEMORY", ":MEMORY:", "a?mode=memoryX")
      files.forEach { f ->
        case("'$f'") { with(DataSourcesConfiguration(KomgaProperties())) { KomgaProperties.Database().apply { file = f }.isMemory() } }
      }
    }

    func("shouldSeparateReadFromWrites") {
      val combos = listOf(":memory:" to JournalMode.WAL, "/db.sqlite" to JournalMode.WAL, "/db.sqlite" to JournalMode.DELETE, "/db.sqlite" to null, "/db.sqlite" to JournalMode.TRUNCATE, "file:x?mode=memory" to JournalMode.WAL)
      combos.forEach { (f, j) ->
        case("$f $j") {
          with(DataSourcesConfiguration(KomgaProperties())) {
            KomgaProperties
              .Database()
              .apply {
                file = f
                journalMode = j
              }.shouldSeparateReadFromWrites()
          }
        }
      }
    }
  }
}
