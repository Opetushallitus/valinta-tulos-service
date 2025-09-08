package fi.vm.sade.valintatulosservice.utils

import fi.vm.sade.valintatulosservice.logging.Logging

object MemoryTracker extends Logging {
  /**
   * NB: Invokes gc both before and after executing <code>block</code>, and sleeps after gcs.
   */
  def memoryUsage[R](blockname: String = "", log: Boolean = true)(block: => R): (R, Long) = {
    val runtime = Runtime.getRuntime

    runGarbageCollector(runtime)
    val memoryUsedBefore = runtime.totalMemory() - runtime.freeMemory()

    val result = block
    runGarbageCollector(runtime)
    val memoryUsedAfter = runtime.totalMemory() - runtime.freeMemory()

    val additionalMemoryUsed: Long = memoryUsedAfter - memoryUsedBefore
    if (log) {
      logger.info(s"$blockname call retained $additionalMemoryUsed bytes " +
        s"(${additionalMemoryUsed / (1024 * 1024)} MB) " +
        s"(went from $memoryUsedBefore to $memoryUsedAfter bytes)")
    }
    (result, additionalMemoryUsed)
  }

  private def runGarbageCollector(runtime: Runtime): Unit = {
    runtime.gc()
  }
}
