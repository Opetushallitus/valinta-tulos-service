package fi.vm.sade.valintatulosservice.vastaanotto

import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.joda.time.DateTime
import java.time.{OffsetDateTime, ZoneOffset}

@RunWith(classOf[JUnitRunner])
class VastaanottoUtilsSpec extends Specification {

  private def getOhjausparametrit: Ohjausparametrit = {
    val vastaanottoaikataulu = Vastaanottoaikataulu(Option(new DateTime(2024,11,11,12,0,0)), Option(7))
    val valintaEsitysHyvaksyttavissa = Option(new DateTime(2024, 11, 9, 12, 0, 0))
    new Ohjausparametrit(vastaanottoaikataulu, None, None, None, None, None, valintaEsitysHyvaksyttavissa, false, true, true)
  }

  "VastaanottoUtils" in {
    "laskeVastaanottoDeadline" in {
      "vastaanotto deadline lisää 7 päivää" in {
        val op = getOhjausparametrit
        val hakutoiveenHyvaksyttyJaJulkaistuDate = Option(OffsetDateTime.of(2024, 11, 10, 11, 20, 15, 0, ZoneOffset.UTC))
        val laskettu = VastaanottoUtils.laskeVastaanottoDeadline(op, hakutoiveenHyvaksyttyJaJulkaistuDate)
        laskettu.get.dayOfMonth.get must beEqualTo(17)
        laskettu.get.hourOfDay.get must beEqualTo(12)
      }
      "vastaanotto deadline lisää 8 päivää kun kellonaika on valintaesityshyväksyttävissä jälkeen" in {
        val op = getOhjausparametrit
        val hakutoiveenHyvaksyttyJaJulkaistuDate = Option(OffsetDateTime.of(2024, 11, 10, 15, 20, 15, 0, ZoneOffset.UTC))
        val laskettu = VastaanottoUtils.laskeVastaanottoDeadline(op, hakutoiveenHyvaksyttyJaJulkaistuDate)
        laskettu.get.dayOfMonth.get must beEqualTo(18)
        laskettu.get.hourOfDay.get must beEqualTo(12)
      }
      "vastaanotto deadline lisää 8 päivää kun kellonaika on minuutteja valintaesityshyväksyttävissä jälkeen" in {
        val op = getOhjausparametrit
        val hakutoiveenHyvaksyttyJaJulkaistuDate = Option(OffsetDateTime.of(2024, 11, 10, 12, 10, 0, 0, ZoneOffset.UTC))
        val laskettu = VastaanottoUtils.laskeVastaanottoDeadline(op, hakutoiveenHyvaksyttyJaJulkaistuDate)
        laskettu.get.dayOfMonth.get must beEqualTo(18)
        laskettu.get.hourOfDay.get must beEqualTo(12)
      }
      "vastaanotto deadline lisää 8 päivää kun kellonaika on sekuntteja valintaesityshyväksyttävissä jälkeen" in {
        val op = getOhjausparametrit
        val hakutoiveenHyvaksyttyJaJulkaistuDate = Option.apply(OffsetDateTime.of(2024, 11, 10, 12, 0, 30, 0, ZoneOffset.UTC))
        val laskettu = VastaanottoUtils.laskeVastaanottoDeadline(op, hakutoiveenHyvaksyttyJaJulkaistuDate)
        laskettu.get.dayOfMonth.get must beEqualTo(18)
        laskettu.get.hourOfDay.get must beEqualTo(12)
      }
    }
  }
}
