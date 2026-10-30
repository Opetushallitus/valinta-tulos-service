package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.HakemuksenTuloksenOrganisaatiorajaus
import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemuksentulos, Hakutoive, HakutoiveenSijoitteluntulos, Hakutoiveentulos}
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Kausi}
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import java.util.Date

@RunWith(classOf[JUnitRunner])
class HakemuksenTuloksenOrganisaatiorajausSpec extends Specification {

  implicit val appConfig: VtsAppConfig.IT = new VtsAppConfig.IT

  val omaHakukohde = HakukohdeOid("1.2.246.562.20.1")
  val toinenOmaHakukohde = HakukohdeOid("1.2.246.562.20.2")
  val vierasHakukohde = HakukohdeOid("1.2.246.562.20.3")
  val sallittu: HakukohdeOid => Boolean = Set(omaHakukohde, toinenOmaHakukohde)

  "HakemuksenTuloksenOrganisaatiorajaus" should {
    "poistaa hakutoiveet, joihin ei ole oikeutta, ja säilyttää muiden järjestyksen" in {
      val rajattu = HakemuksenTuloksenOrganisaatiorajaus.rajaa(tulos(vierasHakukohde, omaHakukohde, toinenOmaHakukohde), sallittu)
      rajattu.map(_.hakutoiveet.map(_.hakukohdeOid)) must beSome(List(omaHakukohde, toinenOmaHakukohde))
    }

    "säilyttää tuloksen muut tiedot" in {
      val alkuperainen = tulos(omaHakukohde, vierasHakukohde)
      val rajattu = HakemuksenTuloksenOrganisaatiorajaus.rajaa(alkuperainen, sallittu)
      rajattu.map(_.copy(hakutoiveet = alkuperainen.hakutoiveet)) must beSome(alkuperainen)
    }

    "palauttaa tuloksen sellaisenaan, kun kaikkiin hakutoiveisiin on oikeus" in {
      val alkuperainen = tulos(omaHakukohde, toinenOmaHakukohde)
      HakemuksenTuloksenOrganisaatiorajaus.rajaa(alkuperainen, sallittu) must beSome(alkuperainen)
    }

    "palauttaa None, kun yhteenkään hakutoiveeseen ei ole oikeutta" in {
      HakemuksenTuloksenOrganisaatiorajaus.rajaa(tulos(vierasHakukohde), sallittu) must beNone
    }

    "palauttaa hakutoiveettoman hakemuksen sellaisenaan, koska mitään ei poistettu" in {
      val alkuperainen = tulos()
      HakemuksenTuloksenOrganisaatiorajaus.rajaa(alkuperainen, sallittu) must beSome(alkuperainen)
    }
  }

  private def tulos(hakukohteet: HakukohdeOid*): Hakemuksentulos = Hakemuksentulos(
    HakuOid("1.2.246.562.29.1"),
    HakemusOid("1.2.246.562.11.1"),
    "1.2.246.562.24.1",
    Vastaanottoaikataulu(Some(new DateTime()), Some(14)),
    hakukohteet.toList.map(toive))

  private val haku = Haku(
    HakuOid("1.2.246.562.29.1"),
    yhteishaku = true,
    korkeakoulu = true,
    toinenAste = false,
    sallittuKohdejoukkoKelaLinkille = true,
    käyttääSijoittelua = true,
    käyttääHakutoiveidenPriorisointia = true,
    varsinaisenHaunOid = None,
    sisältyvätHaut = Set(),
    koulutuksenAlkamiskausi = Some(Kausi("2016S")),
    yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, ""),
    nimi = Map("kieli_fi" -> "Haun nimi"))

  private val ohjausparametrit = Ohjausparametrit(Vastaanottoaikataulu(None, None), Some(DateTime.now().plusDays(10)),
    Some(DateTime.now().plusDays(30)), Some(DateTime.now().plusDays(60)), None, None, None, true, true, true)

  private def toive(hakukohdeOid: HakukohdeOid): Hakutoiveentulos =
    Hakutoiveentulos.julkaistavaVersioSijoittelunTuloksesta(
      Some(new Date()),
      HakutoiveenSijoitteluntulos.kesken(hakukohdeOid, "1.2.246.562.10.1"),
      Hakutoive(hakukohdeOid, "1.2.246.562.10.1", "Hakukohde", "Tarjoaja"),
      haku,
      ohjausparametrit,
      hasHetu = true)
}
