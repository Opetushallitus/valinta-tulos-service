package fi.vm.sade.valintatulosservice.logging

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.slf4j.Logging

trait PerformanceLogger extends Logging {

  //TODO: Make configurable
  val timeLogging: Boolean = true

  private def logTime[R](description: String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    logger.info(s"$description: ${TimeUnit.NANOSECONDS.toMillis(t1 - t0)} ms")
    result
  }

  def time[R](description: String, log: Boolean = true)(block: => R): R =
    if (timeLogging && log) logTime(description) { block }
    else block

}
