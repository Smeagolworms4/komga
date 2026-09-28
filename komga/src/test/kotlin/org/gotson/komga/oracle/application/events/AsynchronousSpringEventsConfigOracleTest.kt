package org.gotson.komga.oracle.application.events

import org.gotson.komga.application.events.AsynchronousSpringEventsConfig
import org.gotson.komga.oracle.OracleTest
import org.springframework.context.ApplicationListener
import org.springframework.context.PayloadApplicationEvent
import org.springframework.core.task.support.TaskExecutorAdapter
import java.util.concurrent.Executor

class AsynchronousSpringEventsConfigOracleTest : OracleTest() {
  override fun cases() {
    val submitted = mutableListOf<Runnable>()
    val received = mutableListOf<Any?>()
    val multicaster = AsynchronousSpringEventsConfig(TaskExecutorAdapter(Executor { submitted.add(it) })).simpleApplicationEventMulticaster()
    multicaster.addApplicationListener(ApplicationListener<PayloadApplicationEvent<*>> { received.add(it.payload) })

    func("simpleApplicationEventMulticaster") {
      case("type") { multicaster::class.simpleName }
      case("listener runs on the executor") {
        multicaster.multicastEvent(PayloadApplicationEvent(this, "hello"))
        listOf(submitted.size, received.toList())
      }
      case("after the executor runs") {
        submitted.forEach { it.run() }
        received.toList()
      }
      case("events keep their order") {
        submitted.clear()
        multicaster.multicastEvent(PayloadApplicationEvent(this, "a"))
        multicaster.multicastEvent(PayloadApplicationEvent(this, "b"))
        submitted.forEach { it.run() }
        received.toList()
      }
    }
  }
}
