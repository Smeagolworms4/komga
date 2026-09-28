package org.gotson.komga.oracle.interfaces.scheduler

import io.mockk.every
import io.mockk.mockk
import org.gotson.komga.application.tasks.TaskEmitter
import org.gotson.komga.infrastructure.search.LuceneEntity
import org.gotson.komga.infrastructure.search.LuceneHelper
import org.gotson.komga.interfaces.scheduler.SearchIndexController
import org.gotson.komga.oracle.OracleTest

class SearchIndexControllerOracleTest : OracleTest() {
  private val calls = mutableListOf<List<Any?>>()
  private val taskEmitter =
    mockk<TaskEmitter>().also {
      every { it.rebuildIndex(any(), any()) } answers { calls.add(listOf("rebuildIndex", firstArg<Int>(), secondArg<Set<LuceneEntity>?>())) }
      every { it.upgradeIndex(any()) } answers { calls.add(listOf("upgradeIndex", firstArg<Int>())) }
    }

  private fun run(
    exists: Boolean,
    version: Int,
  ): List<List<Any?>> {
    val lucene = mockk<LuceneHelper>()
    every { lucene.indexExists() } returns exists
    every { lucene.getIndexVersion() } returns version
    calls.clear()
    SearchIndexController(lucene, taskEmitter).createIndexIfNoneExist()
    return calls.toList()
  }

  override fun cases() {
    func("createIndexIfNoneExist") {
      case("no index") { run(false, 0) }
      listOf(0, 1, 5, 6, 7, 8, 9, 10).forEach { v -> case("version $v") { run(true, v) } }
    }
  }
}
