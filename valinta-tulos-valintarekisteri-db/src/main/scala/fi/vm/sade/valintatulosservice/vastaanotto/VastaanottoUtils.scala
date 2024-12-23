package fi.vm.sade.valintatulosservice.vastaanotto

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
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
      buffer <- bufferOption.map(b => b + Math.max(0, bufferModifier(viimeisinMuutos, deadline)))
    } yield max(viimeisinMuutos, haunValintaesitysHyvaksyttavissa).plusDays(buffer).withZone(DateTimeZone.forID("Europe/Helsinki")).withTime(deadline.getHourOfDay, deadline.getMinuteOfHour, deadline.getSecondOfMinute, deadline.getMillisOfSecond)
  }

  private def bufferModifier(viimeisinMuutos: DateTime, deadline: DateTime): Int = {
    if (viimeisinMuutos.getHourOfDay == deadline.getHourOfDay) {
      if (viimeisinMuutos.getMinuteOfHour == deadline.getMinuteOfHour) {
        return Integer.signum(viimeisinMuutos.getSecondOfMinute - deadline.getSecondOfMinute)
      }
      return Integer.signum(viimeisinMuutos.getMinuteOfHour - deadline.getMinuteOfHour)
    }
    Integer.signum(viimeisinMuutos.getHourOfDay - deadline.getHourOfDay)
  }

  private def max(d1: DateTime, d2: DateTime): DateTime = if(d1.isAfter(d2)) d1 else d2
}
