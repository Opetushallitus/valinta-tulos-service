package fi.vm.sade.valintatulosservice.utils

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter

object TimeUtils {

  val ZONE_FINLAND: ZoneId                        = ZoneId.of("Europe/Helsinki")
  val KOUTA_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    .withZone(ZONE_FINLAND)

  private val KOUTA_DATE_PATTERN = "yyyy-MM-dd"

  val KOUTA_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(KOUTA_DATE_PATTERN)

  def isNowAfter(timeStr: String): Boolean = {
    if (timeStr.length.equals(KOUTA_DATE_PATTERN.length)) {
      val now  = LocalDate.now(ZONE_FINLAND)
      val time = LocalDate.parse(timeStr, KOUTA_DATE_FORMATTER)
      now.isAfter(time)
    } else {
      val now  = LocalDateTime.now(ZONE_FINLAND)
      val time = LocalDateTime.parse(timeStr, KOUTA_DATETIME_FORMATTER)
      now.isAfter(time)
    }
  }
}
