package fi.vm.sade.valintatulosservice.vastaanottomeili

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid, Vastaanottotila}
import fi.vm.sade.valintatulosservice.vastaanottomeili
import fi.vm.sade.valintatulosservice.vastaanottomeili.LahetysSyy._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MailerHelperTest extends Specification {

  private val FI = "fi"

  "MailerHelper.splitAndGroupIlmoitus" should {
    "survive corner cases" in {
      MailerHelper.splitAndGroupIlmoitus(List.empty).size shouldEqual 0
      MailerHelper.splitAndGroupIlmoitus(List(getDummyIlmoitus(List()))) must throwA(new IllegalArgumentException("Empty hakukohdelist in hakemus null"))
    }

    "not split one simple ilmoitus" in {
      val ilmoitus = getDummyIlmoitus(List(vastaanottoilmoitusKk, vastaanottoilmoitusKk))

      val result = MailerHelper.splitAndGroupIlmoitus(List(ilmoitus))

      result.size shouldEqual 1
      result((FI, vastaanottoilmoitusKk)).size shouldEqual 1
      result((FI, vastaanottoilmoitusKk)).head.hakukohteet.size shouldEqual 2
    }

    "splits one simple ilmoitus" in {
      val ilmoitus = getDummyIlmoitus(List(vastaanottoilmoitusKk, sitovan_vastaanoton_ilmoitus))

      val result = MailerHelper.splitAndGroupIlmoitus(List(ilmoitus))

      result.size shouldEqual 2
      result((FI, sitovan_vastaanoton_ilmoitus)).size shouldEqual 1
      result((FI, sitovan_vastaanoton_ilmoitus)).head.hakukohteet.size shouldEqual 1
      result((FI, vastaanottoilmoitusKk)).size shouldEqual 1
      result((FI, vastaanottoilmoitusKk)).head.hakukohteet.size shouldEqual 1
    }

    "group multiple ilmoituses" in {
      val ilmoitus = getDummyIlmoitus(List(vastaanottoilmoitusKk, sitovan_vastaanoton_ilmoitus))
      val ilmoitus2 = getDummyIlmoitus(List(vastaanottoilmoitusKk, sitovan_vastaanoton_ilmoitus))
      val ilmoitus3 = getDummyIlmoitus(List(ehdollisen_periytymisen_ilmoitus))

      val result = MailerHelper.splitAndGroupIlmoitus(List(ilmoitus, ilmoitus2, ilmoitus3))

      result.size shouldEqual 3
      result((FI, sitovan_vastaanoton_ilmoitus)).size shouldEqual 2
      result((FI, sitovan_vastaanoton_ilmoitus)).head.hakukohteet.size shouldEqual 1
      result((FI, vastaanottoilmoitusKk)).size shouldEqual 2
      result((FI, vastaanottoilmoitusKk)).head.hakukohteet.size shouldEqual 1
      result((FI, ehdollisen_periytymisen_ilmoitus)).size shouldEqual 1
      result((FI, ehdollisen_periytymisen_ilmoitus)).head.hakukohteet.size shouldEqual 1
    }
  }

  "MailerHelper.lahetyksenOtsikko" should {
    val hakuOid = HakuOid("haku-oid")
    val hakukohdeOid = HakukohdeOid("hakukohde-oid")
    val hakemusOid = HakemusOid("hakemus-oid")
    val haunNimet = Map(("fi", "Haku"), ("sv", "Haku-sv"), ("en", "Haku-en"))
    val hakukohteenNimet = Map(("fi", "Hakukohde"), ("sv", "Hakukohde-sv"), ("en", "Hakukohde-en"))
    val hakukohde = Hakukohde(hakukohdeOid, LahetysSyy.vastaanottoilmoitusMuut, Vastaanottotila.kesken, false,hakukohteenNimet, Map.empty, Set.empty)
    val haku = Haku(hakuOid, haunNimet, false)
    val ilmoitus = Ilmoitus(hakemusOid, "hakija-oid", None, "fi", "etunimi", "email", None, List(hakukohde), haku)

    "return a constant for AllQuery" in {
      MailerHelper.lahetyksenOtsikko(AllQuery, List.empty) shouldEqual "Kaikki vastaanottosähköpostit"
    }

    "use just oid for HakemusQuery" in {
      val query = HakemusQuery(hakemusOid)

      val result = MailerHelper.lahetyksenOtsikko(query, List.empty)

      result shouldEqual s"Vastaanottosähköpostit hakemukselle hakemus-oid"
    }

    "with HakuQuery" >> {
      "use just oid when name not available" in {
        val query = HakuQuery(hakuOid)
        val ilmoitusWithoutName = ilmoitus.copy(haku = haku.copy(nimi = Map.empty))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutName))

        result shouldEqual s"Vastaanottosähköpostit haulle $hakuOid"
      }

      "use Finnish name of Haku when available" in {
        val query = HakuQuery(hakuOid)

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitus))

        result shouldEqual s"Vastaanottosähköpostit haulle Haku ($hakuOid)"
      }

      "use Swedish name of Haku when Finnish isn't available" in {
        val query = HakuQuery(hakuOid)
        val ilmoitusWithoutFinnish = ilmoitus.copy(haku = haku.copy(nimi = haunNimet.view.filterKeys(_ != "fi").toMap))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutFinnish))

        result shouldEqual s"Vastaanottosähköpostit haulle Haku-sv ($hakuOid)"
      }

      "use English name of Haku when Finnish and Swedish aren't available" in {
        val query = HakuQuery(hakuOid)
        val ilmoitusWithoutFinnish = ilmoitus.copy(haku = haku.copy(nimi = haunNimet.view.filterKeys(_=="en").toMap))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutFinnish))

        result shouldEqual s"Vastaanottosähköpostit haulle Haku-en ($hakuOid)"
      }
    }

    "with HakukohdeQuery" >> {
      "use just oid when name not available" in {
        val query = HakukohdeQuery(hakukohdeOid)
        val ilmoitusWithoutName = ilmoitus.copy(hakukohteet = List(hakukohde.copy(hakukohteenNimet=Map.empty)))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutName))

        result shouldEqual s"Vastaanottosähköpostit hakukohteelle $hakukohdeOid"
      }

      "use just oid when correct hakukohde not in list" in {
        val query = HakukohdeQuery(hakukohdeOid)
        val ilmoitusWithoutName = ilmoitus.copy(hakukohteet = List(hakukohde.copy(oid = HakukohdeOid("toinen-oid"))))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutName))

        result shouldEqual s"Vastaanottosähköpostit hakukohteelle $hakukohdeOid"
      }

      "use Finnish name when available" in {
        val query = HakukohdeQuery(hakukohdeOid)

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitus))

        result shouldEqual s"Vastaanottosähköpostit hakukohteelle Hakukohde ($hakukohdeOid)"
      }
    }

    "with ValintatapajonoQuery" >> {
      "use just oid when name not available" in {
        val query = ValintatapajonoQuery(hakukohdeOid, ValintatapajonoOid("valintatapajono-oid"))
        val ilmoitusWithoutName = ilmoitus.copy(hakukohteet = List(hakukohde.copy(oid = HakukohdeOid("toinen-oid"))))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitusWithoutName))

        result shouldEqual s"Vastaanottosähköpostit hakukohteen $hakukohdeOid valintatapajonolle valintatapajono-oid"
      }

      "use Finnish name when available" in {
        val query = ValintatapajonoQuery(hakukohdeOid, ValintatapajonoOid("valintatapajono-oid"))

        val result = MailerHelper.lahetyksenOtsikko(query, List(ilmoitus))

        result shouldEqual s"Vastaanottosähköpostit hakukohteen Hakukohde ($hakukohdeOid) valintatapajonolle valintatapajono-oid"
      }
    }
  }

  def getDummyIlmoitus(hakukohteidenLahetysSyyt: List[LahetysSyy]): Ilmoitus = {
    vastaanottomeili.Ilmoitus(null, null, None, FI, null, null, null,
      hakukohteidenLahetysSyyt.map(Hakukohde(null, _, null, true, Map.empty, Map.empty, Set.empty)),
      null)
  }

}
