package fi.vm.sade.valintatulosservice

import java.text.SimpleDateFormat

import org.joda.time.DateTimeUtils

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
    DateTimeUtils.setCurrentMillisFixed(millis)
    try {
      f
    }
    finally {
      DateTimeUtils.setCurrentMillisSystem
    }
  }
}
