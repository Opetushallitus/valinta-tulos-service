package fi.vm.sade.valintatulosservice.vastaanotto

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.domain.Vastaanottoaikataulu
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import org.joda.time.{DateTime, DateTimeZone}

object VastaanottoUtils {

  def ehdollinenVastaanottoMahdollista(ohjausparametrit: Ohjausparametrit): Boolean = {
    val now: DateTime = new DateTime()
    val varasijaSaannotVoimassa = ohjausparametrit.varasijaSaannotAstuvatVoimaan.forall(_.isBefore(now))
    val kaikkiJonotSijoittelussa = ohjausparametrit.kaikkiJonotSijoittelussa.forall(_.isBefore(now))
    varasijaSaannotVoimassa && kaikkiJonotSijoittelussa
  }

  def laskeVastaanottoDeadline(ohjausparametrit: Ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate: Option[OffsetDateTime]): Option[DateTime] = {
    ohjausparametrit.vastaanottoaikataulu match {
      case Vastaanottoaikataulu(Some(deadlineFromHaku), buffer) =>
        val deadlineFromHakemuksenTilanMuutos = getDeadlineWithBuffer(ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuDate, buffer, deadlineFromHaku)
        val deadlines = Some(deadlineFromHaku) ++ deadlineFromHakemuksenTilanMuutos
        Some(deadlines.maxBy((a: DateTime) => a.getMillis))
      case _ => None
    }
  }

  private def getDeadlineWithBuffer(ohjausparametrit: Ohjausparametrit, hakutoiveenHyvaksyttyJaJulkaistuOption: Option[OffsetDateTime], bufferOption: Option[Int], deadline: DateTime): Option[DateTime] = {
    for {
      viimeisinMuutos <- hakutoiveenHyvaksyttyJaJulkaistuOption.map(d => new DateTime(
        d.toInstant.toEpochMilli,
        DateTimeZone.forOffsetMillis(Math.toIntExact(TimeUnit.SECONDS.toMillis(d.getOffset.getTotalSeconds)))
      ))
      haunValintaesitysHyvaksyttavissa = ohjausparametrit.valintaesitysHyvaksyttavissa.getOrElse(new DateTime(0))
      buffer <- bufferOption
    } yield max(viimeisinMuutos, haunValintaesitysHyvaksyttavissa).plusDays(buffer).withZone(DateTimeZone.forID("Europe/Helsinki")).withTime(deadline.getHourOfDay, deadline.getMinuteOfHour, deadline.getSecondOfMinute, deadline.getMillisOfSecond)
  }

  private def max(d1: DateTime, d2: DateTime): DateTime = if(d1.isAfter(d2)) d1 else d2
}
