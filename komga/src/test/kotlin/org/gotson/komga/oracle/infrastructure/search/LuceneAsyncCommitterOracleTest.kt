package org.gotson.komga.oracle.infrastructure.search

import io.mockk.every
import io.mockk.mockk
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.ByteBuffersDirectory
import org.gotson.komga.infrastructure.search.LuceneAsyncCommitter
import org.gotson.komga.infrastructure.search.MultiLingualAnalyzer
import org.gotson.komga.infrastructure.search.toDocument
import org.gotson.komga.oracle.OracleTest
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

class LuceneAsyncCommitterOracleTest : OracleTest() {
  /** Scheduler that records the tasks; their futures are done once [done] is set */
  private class Scheduler {
    val tasks = mutableListOf<Pair<Runnable, Instant>>()
    var done = false
    val scheduler =
      mockk<TaskScheduler> {
        every { schedule(any(), any<Instant>()) } answers {
          tasks += firstArg<Runnable>() to secondArg<Instant>()
          mockk<ScheduledFuture<*>> { every { isDone } answers { done } }
        }
      }
  }

  override fun cases() {
    func("commitAndMaybeRefresh") {
      for (delay in listOf(Duration.ofSeconds(2), Duration.ZERO, Duration.ofMinutes(5))) {
        case("delay $delay") {
          val directory = ByteBuffersDirectory()
          val writer = IndexWriter(directory, IndexWriterConfig(MultiLingualAnalyzer()))
          val manager = SearcherManager(writer, SearcherFactory())
          val s = Scheduler()
          val committer = LuceneAsyncCommitter(writer, manager, s.scheduler, delay)
          val query = TermQuery(Term("type", "book"))
          val out = mutableListOf<Any>()
          val start = Instant.now()
          writer.addDocuments(SearchSamples.books.map { it.toDocument() })
          committer.commitAndMaybeRefresh()
          committer.commitAndMaybeRefresh()
          committer.commitAndMaybeRefresh()
          val end = Instant.now()
          // scheduled at now + delay
          out.add(listOf(s.tasks.size, !s.tasks[0].second.isBefore(start.plus(delay)) && !s.tasks[0].second.isAfter(end.plus(delay))))
          out.add(listOf(DirectoryReader.indexExists(directory), manager.acquire().search(query, 100).scoreDocs.size))
          s.tasks[0].first.run()
          out.add(listOf(DirectoryReader.indexExists(directory), manager.acquire().search(query, 100).scoreDocs.size))
          committer.commitAndMaybeRefresh()
          out.add(s.tasks.size)
          s.done = true
          committer.commitAndMaybeRefresh()
          committer.commitAndMaybeRefresh()
          out.add(s.tasks.size)
          writer.deleteDocuments(Term("book_id", "B1"))
          s.tasks.last().first.run()
          out.add(manager.acquire().search(query, 100).scoreDocs.size)
          out
        }
      }
    }
  }
}
