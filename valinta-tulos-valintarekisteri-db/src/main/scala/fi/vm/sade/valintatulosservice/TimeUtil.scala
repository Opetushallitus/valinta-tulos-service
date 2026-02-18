package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.TimeUtil.timezoneFi

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalTime, OffsetDateTime, ZoneId, ZonedDateTime}

class TimeUtil {
  def currentDateTime: ZonedDateTime = ZonedDateTime.now(timezoneFi)

  def currentlyWithin(alku: Option[ZonedDateTime], loppu: Option[ZonedDateTime]): Boolean = {
    val now = currentDateTime
    alku.forall(now.isAfter(_)) && loppu.forall(now.isBefore(_))
  }

  def isBeforeNow(dateTime: ZonedDateTime): Boolean =
    dateTime.isBefore(currentDateTime)

  def nowIsAfter(dateTime: ZonedDateTime): Boolean =
    currentDateTime.isAfter(dateTime)
}

object TimeUtil {
  val timezoneFi: ZoneId = ZoneId.of("Europe/Helsinki")
  private val instance = new TimeUtil()

  def apply(): TimeUtil = instance

  def atEndOfDay(dateTime: ZonedDateTime): ZonedDateTime =
    dateTime.`with`(LocalTime.MAX).truncatedTo(ChronoUnit.MILLIS)

  def utilDateToZonedDateTime(date: java.util.Date): ZonedDateTime =
    ZonedDateTime.ofInstant(date.toInstant, timezoneFi)

  def epochMillisToZonedDateTime(millis: Long): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), timezoneFi)

  def inFinnishTimezone(date: OffsetDateTime): OffsetDateTime =
    date.atZoneSameInstant(TimeUtil.timezoneFi).toOffsetDateTime
}
