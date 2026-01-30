package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

object TimeWarp {
  private val defaultZone: ZoneId = ZoneId.of("Europe/Helsinki")
  private val systemClock: Clock = Clock.system(defaultZone)
  @volatile private var currentClock: Clock = systemClock

  // A delegating clock that always goes through the mutable currentClock, so it picks up time changes
  val clock: Clock = new Clock {
    override def getZone: ZoneId = currentClock.getZone
    override def withZone(zone: ZoneId): Clock = currentClock.withZone(zone)
    override def instant(): Instant = currentClock.instant()
  }

  def now(): ZonedDateTime = ZonedDateTime.now(currentClock)

  def setFixedTime(millis: Long): Unit = {
    currentClock = Clock.fixed(Instant.ofEpochMilli(millis), defaultZone)
  }

  def resetTime(): Unit = {
    currentClock = systemClock
  }
}

trait TimeWarp {
  def parseDate(dateTime: String) = {
    new SimpleDateFormat("d.M.yyyy HH:mm").parse(dateTime)
  }

  def getMillisFromTime(dateTime: String) = {
    parseDate(dateTime).getTime
  }

  def withFixedDateTime[T](dateTime: String)(f: => T):T = {
    withFixedDateTime(getMillisFromTime(dateTime))(f)
  }

  def withFixedDateTime[T](millis: Long)(f: => T) = {
    TimeWarp.setFixedTime(millis)
    try {
      f
    }
    finally {
      TimeWarp.resetTime()
    }
  }
}
