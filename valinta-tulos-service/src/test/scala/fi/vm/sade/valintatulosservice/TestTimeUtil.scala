package fi.vm.sade.valintatulosservice

import org.specs2.mock.Mockito

import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * TimeUtil, jonka tämänhetkinen aika voidaan ylikirjoittaa  `withFixedDateTime` -metodeilla.
 */
class TestTimeUtil extends TimeUtil {
  private var timeOverride: Option[ZonedDateTime] = None

  override def currentDateTime: ZonedDateTime = timeOverride.getOrElse(super.currentDateTime)

  def withFixedDateTime[T](dateStr: String)(f: => T): T =
    withFixedDateTime(TestTimeUtil.parseDateTime(dateStr))(f)

  def withFixedDateTime[T](dateTime: ZonedDateTime)(f: => T): T = {
    timeOverride = Some(dateTime)
    try {
      f
    } finally {
      timeOverride = None
    }
  }
}

object TestTimeUtil extends Mockito {
  private val formatter = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm").withZone(TimeUtil.timezoneFi)

  def apply() = new TestTimeUtil()

  def parseDateTime(dateStr: String): ZonedDateTime =
    ZonedDateTime.parse(dateStr, formatter)

  def parseDate(dateTime: String): Date = {
    new SimpleDateFormat("d.M.yyyy HH:mm").parse(dateTime)
  }

}
