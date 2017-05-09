package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import fi.vm.sade.valintatulosservice.{ITSpecification, TimeWarp}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import slick.dbio.DBIO

@RunWith(classOf[JUnitRunner])
class ValintarekisteriValintatulosDaoSpec extends Specification with ITSetup with ValintarekisteriDbTools {
  sequential
  step(appConfig.start)
  step(deleteAll())

  val hakuOid1 = HakuOid("Haku1")
  val hakuOid2 = HakuOid("Haku2")

  val oidHaku1hakukohde1 = HakukohdeOid("haku1.hakukohde1")
  val oidHaku1hakukohde2 = HakukohdeOid("haku1.hakukohde2")
  val oidHaku2hakukohde1 = HakukohdeOid("haku2.hakukohde1")

  val oidHaku1hakukohde1jono1 = ValintatapajonoOid("haku1.hakukohde1.valintatapajono1")
  val oidHaku1hakukohde2jono1 = ValintatapajonoOid("haku1.hakukohde2.valintatapajono1")
  val oidHaku2hakukohde1jono1 = ValintatapajonoOid("haku2.hakukohde1.valintatapajono1")
  val oidHaku2hakukohde1jono2 = ValintatapajonoOid("haku2.hakukohde1.valintatapajono2")

  val hakemusOid6 = HakemusOid("hakemus6")

  lazy val dao = new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb)

  step(createTestData)

  "load valintatulokset for haku" in {
    val hakukohdeOids = dao.loadValintatulokset(hakuOid1).map(_.getHakemusOid)
    hakukohdeOids.size must_== 4
    List("hakemus1", "hakemus2", "hakemus3", "hakemus4").diff(hakukohdeOids) must_== List()
  }

  "load valintatulokset for hakukohde" in {
    val hakukohdeOids = dao.loadValintatuloksetForHakukohde(oidHaku2hakukohde1).map(_.getHakemusOid)
    hakukohdeOids.size must_== 4
    List("hakemus5", "hakemus6", "hakemus7", "hakemus8").diff(hakukohdeOids) must_== List()
  }

  "load valintatulokset for hakemus" in {
    val valintatulokset1 = dao.loadValintatuloksetForHakemus(hakemusOid6)
    valintatulokset1.size must_== 1
    valintatulokset1.head.getHakemusOid must_== hakemusOid6.toString

    insertValinnantulos(hakuOid2, valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, hakemusOid6))

    val valintatulokset2 = dao.loadValintatuloksetForHakemus(hakemusOid6)
    valintatulokset2.size must_== 2
    valintatulokset2.map(_.getHakemusOid).distinct.diff(List(hakemusOid6.toString)) must_== List()
    valintatulokset2.map(_.getTila).diff(List(ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)) must_== List()
    valintatulokset2.map(vt => ValintatapajonoOid(vt.getValintatapajonoOid)).diff(List(oidHaku2hakukohde1jono1, oidHaku2hakukohde1jono2)) must_== List()
  }

  private def createTestData() = {
    singleConnectionValintarekisteriDb.storeHakukohde(new HakukohdeRecord(oidHaku1hakukohde1, hakuOid1, false, false, Kevat(2017)))
    singleConnectionValintarekisteriDb.storeHakukohde(new HakukohdeRecord(oidHaku1hakukohde2, hakuOid1, false, false, Kevat(2017)))
    singleConnectionValintarekisteriDb.storeHakukohde(new HakukohdeRecord(oidHaku2hakukohde1, hakuOid2, false, false, Kevat(2017)))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde1, oidHaku1hakukohde1jono1, HakemusOid("hakemus1")))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde1, oidHaku1hakukohde1jono1, HakemusOid("hakemus2")))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde2, oidHaku1hakukohde2jono1, HakemusOid("hakemus3")))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde2, oidHaku1hakukohde2jono1, HakemusOid("hakemus4")))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, HakemusOid("hakemus5")))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, HakemusOid("hakemus6")))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, HakemusOid("hakemus7")))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, HakemusOid("hakemus8")))
  }

  private def valinnantulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) = {
    Valinnantulos(
      hakukohdeOid = hakukohdeOid,
      valintatapajonoOid = valintatapajonoOid,
      hakemusOid = hakemusOid,
      henkiloOid = hakemusOid.toString,
      valinnantila = Hyvaksytty,
      ehdollisestiHyvaksyttavissa = None,
      ehdollisenHyvaksymisenEhtoKoodi = None,
      ehdollisenHyvaksymisenEhtoFI = None,
      ehdollisenHyvaksymisenEhtoSV = None,
      ehdollisenHyvaksymisenEhtoEN = None,
      julkaistavissa = None,
      hyvaksyttyVarasijalta = None,
      hyvaksyPeruuntunut = None,
      vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
      ilmoittautumistila = Lasna)
  }

  private def valinnantulosHylatty(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) = {
    Valinnantulos(
      hakukohdeOid = hakukohdeOid,
      valintatapajonoOid = valintatapajonoOid,
      hakemusOid = hakemusOid,
      henkiloOid = hakemusOid.toString,
      valinnantila = Hylatty,
      ehdollisestiHyvaksyttavissa = None,
      ehdollisenHyvaksymisenEhtoKoodi = None,
      ehdollisenHyvaksymisenEhtoFI = None,
      ehdollisenHyvaksymisenEhtoSV = None,
      ehdollisenHyvaksymisenEhtoEN = None,
      julkaistavissa = None,
      hyvaksyttyVarasijalta = None,
      hyvaksyPeruuntunut = None,
      vastaanottotila = ValintatuloksenTila.KESKEN,
      ilmoittautumistila = EiTehty)
  }

  private def insertValinnantulos(hakuOid:HakuOid, valinnantulos: Valinnantulos): Unit = {
    singleConnectionValintarekisteriDb.runBlocking(DBIO.sequence(List(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantulos.getValinnantilanTallennus("muokkaaja")),
        singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(valinnantulos.getValinnantuloksenOhjaus("muokkaaja", "selite"))
    )))
    if(valinnantulos.vastaanottotila == ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) {
      singleConnectionValintarekisteriDb.runBlocking(DBIO.sequence(List(
        singleConnectionValintarekisteriDb.storeAction(VirkailijanVastaanotto(hakuOid, valinnantulos.valintatapajonoOid, valinnantulos.henkiloOid,
          valinnantulos.hakemusOid, valinnantulos.hakukohdeOid, VastaanotaSitovasti, "muokkaaja", "selite")),
        singleConnectionValintarekisteriDb.storeIlmoittautuminen(valinnantulos.henkiloOid, Ilmoittautuminen(valinnantulos.hakukohdeOid, valinnantulos.ilmoittautumistila, "muokkaaja", "selite"))
      )))
    }
  }
}
