package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat
import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

object TimeWarp {
  def clock: Clock = ClockHolder.clock

  def now(): ZonedDateTime = ClockHolder.now()

  def setFixedTime(millis: Long): Unit = {
    ClockHolder.setClock(Clock.fixed(Instant.ofEpochMilli(millis), ZoneId.of("Europe/Helsinki")))
  }

  def resetTime(): Unit = {
    ClockHolder.resetClock()
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
