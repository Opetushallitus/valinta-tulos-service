package fi.vm.sade.valintatulosservice

import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

/**
 * Provides a central Clock instance that can be overridden for testing.
 * In production, this uses the system default clock.
 * In tests, TimeWarp can set a fixed clock to control time-dependent behavior.
 */
object ClockHolder {
  @volatile private var _clock: Clock = Clock.systemDefaultZone()

  def clock: Clock = _clock

  def setClock(clock: Clock): Unit = {
    _clock = clock
  }

  def resetClock(): Unit = {
    _clock = Clock.systemDefaultZone()
  }

  def now(): ZonedDateTime = ZonedDateTime.now(clock)

  def instant(): Instant = clock.instant()
}
