package fi.vm.sade.valintatulosservice.utils

import fi.vm.sade.valintatulosservice.tarjonta.Hakukohde

import java.time.{LocalDate, LocalDateTime, ZoneId, ZonedDateTime}
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

  def getPaateltyAloitusajankohta(hakukohde: Hakukohde): String = {
    hakukohde.paateltyAlkamisajankohta.flatMap(ajankohta =>
      (ajankohta.pvm, ajankohta.pvm.isBlank, ajankohta.henkilokohtainenSuunnitelma) match {
        case (_, true, false) =>
          None
        case (pvm, false, false) =>
          if (TimeUtils.isNowAfter(pvm)) {
            Some(TimeUtils.KOUTA_DATE_FORMATTER.format(ZonedDateTime.now(TimeUtils.ZONE_FINLAND)))
          } else {
            Some(pvm)
          }
        case (_, _, true) =>
          Some(TimeUtils.KOUTA_DATE_FORMATTER.format(ZonedDateTime.now(TimeUtils.ZONE_FINLAND)))
      }).orNull
  }
}
