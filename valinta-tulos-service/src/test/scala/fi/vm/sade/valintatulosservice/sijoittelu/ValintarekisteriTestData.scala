package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.ITSetup
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

trait ValintarekisteriTestData extends ValintarekisteriDbTools {

  val singleConnectionValintarekisteriDb:ValintarekisteriDb

  val hakuOid1 = HakuOid("Haku1")
  val hakuOid2 = HakuOid("Haku2")

  val oidHaku1hakukohde1 = HakukohdeOid("haku1.hakukohde1")
  val oidHaku1hakukohde2 = HakukohdeOid("haku1.hakukohde2")
  val oidHaku2hakukohde1 = HakukohdeOid("haku2.hakukohde1")

  val oidHaku1hakukohde1jono1 = ValintatapajonoOid("haku1.hakukohde1.valintatapajono1")
  val oidHaku1hakukohde2jono1 = ValintatapajonoOid("haku1.hakukohde2.valintatapajono1")
  val oidHaku2hakukohde1jono1 = ValintatapajonoOid("haku2.hakukohde1.valintatapajono1")
  val oidHaku2hakukohde1jono2 = ValintatapajonoOid("haku2.hakukohde1.valintatapajono2")

  val hakemusOid1 = HakemusOid("hakemus1")
  val hakemusOid2 = HakemusOid("hakemus2")
  val hakemusOid3 = HakemusOid("hakemus3")
  val hakemusOid4 = HakemusOid("hakemus4")
  val hakemusOid5 = HakemusOid("hakemus5")
  val hakemusOid6 = HakemusOid("hakemus6")
  val hakemusOid7 = HakemusOid("hakemus7")
  val hakemusOid8 = HakemusOid("hakemus8")

  val sijoitteluajoId = 12345l

  val sijoittelunHakemusOid1 = HakemusOid("Haku2.2.1.1")
  val sijoittelunHakemusOid2 = HakemusOid("Haku2.2.1.2")
  val sijoittelunHakukohdeOid1 = HakukohdeOid("Haku2.1")
  val sijoittelunHakukohdeOid2 = HakukohdeOid("Haku2.2")
  val sijoittelunValintatapajonoOid1 = ValintatapajonoOid("Haku2.1.1")
  val sijoittelunValintatapajonoOid2 = ValintatapajonoOid("Haku2.2.1")

  def createSijoitteluajoHaulle2() = {
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(sijoitteluajoId, hakuOid2, 2))
  }

  def createHakujen1Ja2ValinnantuloksetIlmanSijoittelua() = {
    singleConnectionValintarekisteriDb.storeHakukohde(EiKktutkintoonJohtavaHakukohde(oidHaku1hakukohde1, hakuOid1, Some(Kevat(2017))))
    singleConnectionValintarekisteriDb.storeHakukohde(EiKktutkintoonJohtavaHakukohde(oidHaku1hakukohde2, hakuOid1, Some(Kevat(2017))))
    singleConnectionValintarekisteriDb.storeHakukohde(EiKktutkintoonJohtavaHakukohde(oidHaku2hakukohde1, hakuOid2, Some(Kevat(2017))))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde1, oidHaku1hakukohde1jono1, hakemusOid1))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde1, oidHaku1hakukohde1jono1, hakemusOid2))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde2, oidHaku1hakukohde2jono1, hakemusOid3))
    insertValinnantulos(hakuOid1, valinnantulos(oidHaku1hakukohde2, oidHaku1hakukohde2jono1, hakemusOid4))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, hakemusOid5))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, hakemusOid6))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, hakemusOid7))
    insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, hakemusOid8))
  }

  def valinnantulos(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) = {
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
      valinnantilanKuvauksenTekstiFI = None,
      valinnantilanKuvauksenTekstiSV = None,
      valinnantilanKuvauksenTekstiEN = None,
      julkaistavissa = Some(true),
      hyvaksyttyVarasijalta = None,
      hyvaksyPeruuntunut = None,
      vastaanottotila = ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
      ilmoittautumistila = Lasna)
  }

  def valinnantulosHylatty(hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid, hakemusOid:HakemusOid) = {
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
      valinnantilanKuvauksenTekstiFI = None,
      valinnantilanKuvauksenTekstiSV = None,
      valinnantilanKuvauksenTekstiEN = None,
      julkaistavissa = None,
      hyvaksyttyVarasijalta = None,
      hyvaksyPeruuntunut = None,
      vastaanottotila = ValintatuloksenTila.KESKEN,
      ilmoittautumistila = EiTehty)
  }

  def insertValinnantulos(hakuOid:HakuOid, valinnantulos: Valinnantulos): Unit = {
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
