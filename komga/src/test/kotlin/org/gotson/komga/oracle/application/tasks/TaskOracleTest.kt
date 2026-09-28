package org.gotson.komga.oracle.application.tasks

import org.gotson.komga.application.tasks.HIGHEST_PRIORITY
import org.gotson.komga.application.tasks.LOWEST_PRIORITY
import org.gotson.komga.application.tasks.Task
import org.gotson.komga.domain.model.BookMetadataPatchCapability
import org.gotson.komga.domain.model.BookPageNumbered
import org.gotson.komga.domain.model.CopyMode
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.oracle.OracleDb
import org.gotson.komga.oracle.OracleTest

class TaskOracleTest : OracleTest() {
  private val mapper = OracleDb().use { it.mapper }

  /** simple name, uniqueId, priority, groupId, toString, JSON payload, (sorted) toString, uniqueId and groupId after a JSON round trip */
  private fun t(task: Task): List<Any?> {
    val json = mapper.writeValueAsString(task)
    // after a JSON round trip, sets are HashSets whose order depends on the identity hash of enums: sorted characters
    val back = mapper.readValue(json, task.javaClass)
    return listOf(
      task.javaClass.simpleName,
      task.uniqueId,
      task.priority,
      task.groupId,
      task.toString(),
      json,
      back
        .toString()
        .toList()
        .sorted()
        .joinToString(""),
      back.uniqueId,
      back.groupId,
    )
  }

  private val pages =
    listOf(
      BookPageNumbered("p1.jpg", "image/jpeg", Dimension(10, 20), "hash1", 123L, 1),
      BookPageNumbered("p2.png", "image/png", pageNumber = 2),
    )

  override fun cases() {
    func("toString@27") {
      case("default") { t(Task.ScanLibrary("L1", false)) }
      case("deep, priority") { t(Task.ScanLibrary("L'1", true, HIGHEST_PRIORITY)) }
    }
    func("toString@36") {
      case("default") { t(Task.FindBooksToConvert("L1")) }
      case("priority") { t(Task.FindBooksToConvert("L1", -3)) }
    }
    func("toString@45") {
      case("default") { t(Task.FindBooksWithMissingPageHash("L1")) }
      case("priority") { t(Task.FindBooksWithMissingPageHash("", LOWEST_PRIORITY)) }
    }
    func("toString@54") {
      case("default") { t(Task.FindDuplicatePagesToDelete("L1")) }
      case("priority") { t(Task.FindDuplicatePagesToDelete("L 2", 7)) }
    }
    func("toString@63") {
      case("default") { t(Task.EmptyTrash("L1")) }
      case("priority") { t(Task.EmptyTrash("L1", 100)) }
    }
    func("toString@73") {
      case("default") { t(Task.AnalyzeBook("B1", groupId = "S1")) }
      case("priority") { t(Task.AnalyzeBook("B1", 6, "S\"1")) }
    }
    func("toString@82") {
      case("default") { t(Task.GenerateBookThumbnail("B1")) }
      case("priority") { t(Task.GenerateBookThumbnail("B1", 1)) }
    }
    func("toString@93") {
      case("all capabilities") { t(Task.RefreshBookMetadata("B1", BookMetadataPatchCapability.entries.toSet(), groupId = "S1")) }
      case("some capabilities") { t(Task.RefreshBookMetadata("B1", setOf(BookMetadataPatchCapability.TAGS, BookMetadataPatchCapability.TITLE), 2, "S1")) }
      case("no capability") { t(Task.RefreshBookMetadata("B1", emptySet(), groupId = "S1")) }
    }
    func("toString@102") {
      case("default") { t(Task.HashBook("B1")) }
      case("priority") { t(Task.HashBook("B1", LOWEST_PRIORITY)) }
    }
    func("toString@111") {
      case("default") { t(Task.HashBookPages("B1")) }
      case("priority") { t(Task.HashBookPages("B1", 5)) }
    }
    func("toString@120") {
      case("default") { t(Task.HashBookKoreader("B1")) }
      case("priority") { t(Task.HashBookKoreader("B1", 0)) }
    }
    func("toString@129") {
      case("default") { t(Task.RefreshSeriesMetadata("S1")) }
      case("priority") { t(Task.RefreshSeriesMetadata("S1", 8)) }
    }
    func("toString@138") {
      case("default") { t(Task.AggregateSeriesMetadata("S1")) }
      case("priority") { t(Task.AggregateSeriesMetadata("S1", 3)) }
    }
    func("toString@147") {
      case("default") { t(Task.RefreshBookLocalArtwork("B1")) }
      case("priority") { t(Task.RefreshBookLocalArtwork("B1", 9)) }
    }
    func("toString@156") {
      case("default") { t(Task.RefreshSeriesLocalArtwork("S1")) }
      case("priority") { t(Task.RefreshSeriesLocalArtwork("S1", 2)) }
    }
    func("toString@169") {
      case("nulls") { t(Task.ImportBook("/tmp/a b.cbz", "S1", CopyMode.COPY, null, null)) }
      case("all fields") { t(Task.ImportBook("/tmp/é.cbz", "S1", CopyMode.HARDLINK, "dest name", "B9", 6)) }
      case("move") { t(Task.ImportBook("", "S1", CopyMode.MOVE, "", "")) }
    }
    func("toString@179") {
      case("default") { t(Task.ConvertBook("B1", groupId = "S1")) }
      case("priority") { t(Task.ConvertBook("B1", 5, "S2")) }
    }
    func("toString@189") {
      case("default") { t(Task.RepairExtension("B1", groupId = "S1")) }
      case("priority") { t(Task.RepairExtension("B1", 3, "S2")) }
    }
    func("toString@199") {
      case("pages") { t(Task.RemoveHashedPages("B1", pages)) }
      case("no page") { t(Task.RemoveHashedPages("B1", emptyList(), 1)) }
    }
    func("toString@208") {
      case("all entities") { t(Task.RebuildIndex(null)) }
      case("some entities") { t(Task.RebuildIndex(setOf(LuceneEntity.Series, LuceneEntity.Book), 6)) }
      case("no entity") { t(Task.RebuildIndex(emptySet())) }
    }
    func("toString@216") {
      case("default") { t(Task.UpgradeIndex()) }
      case("priority") { t(Task.UpgradeIndex(0)) }
    }
    func("toString@225") {
      case("default") { t(Task.DeleteBook("B1")) }
      case("priority") { t(Task.DeleteBook("B1", 8)) }
    }
    func("toString@234") {
      case("default") { t(Task.DeleteSeries("S1")) }
      case("priority") { t(Task.DeleteSeries("S1", 7)) }
    }
    func("toString@243") {
      case("default") { t(Task.FindBookThumbnailsToRegenerate(false)) }
      case("priority") { t(Task.FindBookThumbnailsToRegenerate(true, 1)) }
    }
  }
}
