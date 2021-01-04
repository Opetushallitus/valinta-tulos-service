package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.config.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakuOid,
  HakukohdeOid,
  Kausi,
  Vastaanottotila
}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HakutoiveenIlmoittautumistilaSpec extends Specification {
  val vastaanottanut = {
    val kesken: HakutoiveenSijoitteluntulos =
      HakutoiveenSijoitteluntulos.kesken(HakukohdeOid(""), "")
    kesken.copy(hakijanTilat =
      kesken.hakijanTilat.copy(vastaanottotila = Vastaanottotila.vastaanottanut)
    )
  }
  "Ilmoittautuminen" should {
    "should be enabled in IT" in {
      implicit val appConfig = new VtsAppConfig.IT
      val it = HakutoiveenIlmoittautumistila.getIlmoittautumistila(
        vastaanottanut,
        Haku(
          HakuOid(""),
          korkeakoulu = true,
          toinenAste = false,
          sallittuKohdejoukkoKelaLinkille = true,
          käyttääSijoittelua = true,
          käyttääHakutoiveidenPriorisointia = true,
          varsinaisenHaunOid = None,
          sisältyvätHaut = Set(),
          hakuAjat = Nil,
          koulutuksenAlkamiskausi = Some(Kausi("2016S")),
          yhdenPaikanSaanto = YhdenPaikanSaanto(false, ""),
          nimi = Map("kieli_fi" -> "Haun nimi")
        ),
        Ohjausparametrit.empty,
        hasHetu = true
      )
      it.ilmoittauduttavissa must_== true
    }

    "should be disabled by default" in {
      implicit val appConfig = new VtsAppConfig.IT_disabledIlmoittautuminen
      val it = HakutoiveenIlmoittautumistila.getIlmoittautumistila(
        vastaanottanut,
        Haku(
          HakuOid(""),
          korkeakoulu = true,
          toinenAste = false,
          sallittuKohdejoukkoKelaLinkille = true,
          käyttääSijoittelua = true,
          käyttääHakutoiveidenPriorisointia = true,
          varsinaisenHaunOid = None,
          sisältyvätHaut = Set(),
          hakuAjat = Nil,
          koulutuksenAlkamiskausi = Some(Kausi("2016S")),
          yhdenPaikanSaanto = YhdenPaikanSaanto(false, ""),
          nimi = Map("kieli_fi" -> "Haun nimi")
        ),
        Ohjausparametrit.empty,
        hasHetu = false
      )
      it.ilmoittauduttavissa must_== false
    }
  }
}
