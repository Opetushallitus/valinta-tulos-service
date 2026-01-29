package fi.vm.sade.valintatulosservice.vastaanotto

import java.time.{Instant, OffsetDateTime, ZoneId, ZoneOffset, ZonedDateTime}

import fi.vm.sade.valintatulosservice.ClockHolder
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}

object VastaanottoUtils {

  private val helsinkiZone = ZoneId.of("Europe/Helsinki")

  def ehdollinenVastaanottoMahdollista(ohjausparametrit: Ohjausparametrit): Boolean = {
    val now: ZonedDateTime = ClockHolder.now()
    val varasijaSaannotVoimassa = ohjausparametrit.varasijaSaannotAstuvatVoimaan.forall(_.isBefore(now))
    val kaikkiJonotSijoittelussa = ohjausparametrit.kaikkiJonotSijoittelussa.forall(_.isBefore(now))
    varasijaSaannotVoimassa && kaikkiJonotSijoittelussa
  }

  def laskeVastaanottoDeadline(ohjausparametrit: Ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime]): Option[ZonedDateTime] = {
    ohjausparametrit.vastaanottoaikataulu match {
      case Vastaanottoaikataulu(Some(deadlineFromHaku), buffer) =>
        val deadlineFromHakemuksenTilanMuutos = getDeadlineWithBuffer(ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, buffer, deadlineFromHaku)
        val deadlines = Some(deadlineFromHaku) ++ deadlineFromHakemuksenTilanMuutos
        Some(deadlines.maxBy((a: ZonedDateTime) => a.toInstant.toEpochMilli))
      case _ => None
    }
  }

  private def getDeadlineWithBuffer(ohjausparametrit: Ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuOption: Option[OffsetDateTime], bufferOption: Option[Int], deadline: ZonedDateTime): Option[ZonedDateTime] = {
    for {
      viimeisinMuutos <- hakutoiveenHyvaksyttyJaJulkaistuOption.map(d =>
        ZonedDateTime.ofInstant(d.toInstant, ZoneOffset.ofTotalSeconds(d.getOffset.getTotalSeconds))
      )
      haunValintaesitysHyvaksyttavissa = ohjausparametrit.valintaesitysHyvaksyttavissa.getOrElse(ZonedDateTime.ofInstant(Instant.EPOCH, helsinkiZone))
      buffer <- bufferOption.map(b => b + bufferModifier(viimeisinMuutos, deadline))
    } yield max(viimeisinMuutos, haunValintaesitysHyvaksyttavissa)
      .plusDays(buffer)
      .withZoneSameInstant(helsinkiZone)
      .withHour(deadline.getHour)
      .withMinute(deadline.getMinute)
      .withSecond(deadline.getSecond)
      .withNano(deadline.getNano)
  }

  //Palauttaa 1 jos viimeisimmän muutoksen kellonaika on ollut ennen eräpäivän kellonaikaa, 0 muussa tapauksessa
  private def bufferModifier(viimeisinMuutos: ZonedDateTime, deadline: ZonedDateTime): Int = {
    var modifier: Int = 0
    if (viimeisinMuutos.getHour == deadline.getHour) {
      if (viimeisinMuutos.getMinute == deadline.getMinute) {
        modifier = Integer.signum(viimeisinMuutos.getSecond - deadline.getSecond)
      } else {
        modifier = Integer.signum(viimeisinMuutos.getMinute - deadline.getMinute)
      }
    } else {
      modifier = Integer.signum(viimeisinMuutos.getHour - deadline.getHour)
    }
    Math.max(0, modifier)
  }

  private def max(d1: ZonedDateTime, d2: ZonedDateTime): ZonedDateTime = if(d1.isAfter(d2)) d1 else d2
}
