package fi.vm.sade.valintatulosservice.utils

import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, Hakukohde, PaateltyAlkamisajankohta, TarjontaHakuService}
import fi.vm.sade.valintatulosservice.utils.TimeUtils.{KOUTA_DATETIME_FORMATTER, KOUTA_DATE_FORMATTER}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.time.{LocalDate, LocalDateTime}
import java.time.temporal.{ChronoUnit, TemporalUnit}

@RunWith(classOf[JUnitRunner])
class TimeUtilsTest extends Specification {

  "TimeUtils getPaateltyAloitusajankohta" should {
    "palauttaa nullin jos ei annettu pvm eikä henkilökohtainen suunnitelma" in {
      TimeUtils.getPaateltyAloitusajankohta(createHakukohde(PaateltyAlkamisajankohta(pvm = None, henkilokohtainenSuunnitelma = false))) must beNull
    }

    "palauttaa nykyhetken henkilökohtaiselle suunnitelmalle" in {
      val result = TimeUtils.getPaateltyAloitusajankohta(createHakukohde(PaateltyAlkamisajankohta(pvm = Some("2099-09-19"), henkilokohtainenSuunnitelma = true)))
      val now = KOUTA_DATE_FORMATTER.format(LocalDate.now)
      result must_== now
    }

    "palauttaa nykyhetken päivämäärälle joka on menneisyydessä" in {
      val result = TimeUtils.getPaateltyAloitusajankohta(createHakukohde(PaateltyAlkamisajankohta(pvm = Some("2026-07-15"), henkilokohtainenSuunnitelma = false)))
      val now = KOUTA_DATE_FORMATTER.format(LocalDate.now)
      result must_== now
    }

    "palauttaa annetun päivämäärän kun se on tulevaisuudessa" in {
      val tomorrow = KOUTA_DATE_FORMATTER.format(LocalDate.now.plusDays(1))
      val result = TimeUtils.getPaateltyAloitusajankohta(createHakukohde(PaateltyAlkamisajankohta(pvm = Some(tomorrow), henkilokohtainenSuunnitelma = false)))
      result must_== tomorrow
    }
  }

  "TimeUtils isNowAfter" should {
    "palauttaa true jos aika on menneisyydessä" in {
      TimeUtils.isNowAfter("2026-07-15") must beTrue
      TimeUtils.isNowAfter("2026-07-15T12:55:45") must beTrue
    }

    "palauttaa false jos aika täsmää" in {
      TimeUtils.isNowAfter(KOUTA_DATE_FORMATTER.format(LocalDate.now)) must beFalse
    }

    "palauttaa false jos aika on tulevaisuudessa" in {
      TimeUtils.isNowAfter(KOUTA_DATE_FORMATTER.format(LocalDate.now.plusDays(1))) must beFalse
      TimeUtils.isNowAfter(KOUTA_DATETIME_FORMATTER.format(LocalDateTime.now.plusSeconds(1))) must beFalse
    }
  }

  private def createHakukohde(paatelty: PaateltyAlkamisajankohta) = {
    Hakukohde(
      oid = HakukohdeOid(""),
      hakuOid = HakuOid(""),
      tarjoajaOids = Set.empty,
      koulutusAsteTyyppi = "",
      hakukohteenNimet = Map.empty,
      tarjoajaNimet = Map.empty,
      yhdenPaikanSaanto = null,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = None,
      koulutuksenAlkamisvuosi = None,
      organisaatioRyhmaOids = Set.empty,
      hakukohteenNimiUri = None,
      paateltyAlkamisajankohta = Some(paatelty))
  }
}