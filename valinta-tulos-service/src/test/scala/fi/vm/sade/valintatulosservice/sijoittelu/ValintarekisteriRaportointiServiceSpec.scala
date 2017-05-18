package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.KevytHakijaDTO
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.valintarekisteri.ITSetup
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriRaportointiServiceSpec extends ITSpecification with ValintarekisteriTestData {
  step(deleteAll())

  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(singleConnectionValintarekisteriDb, new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb))
  lazy val client = new ValintarekisteriSijoittelunTulosClientImpl(singleConnectionValintarekisteriDb, singleConnectionValintarekisteriDb)

  step(createSijoitteluajoHaulle2)
  step(createHakujen1Ja2ValinnantuloksetIlmanSijoittelua)

  lazy val sijoitteluajo = raportointiService.getSijoitteluAjo(sijoitteluajoId).get
  lazy val erillishaku = client.fetchLatestSijoitteluAjo(hakuOid1).get

  def assertErillishakuHakija(hakija:KevytHakijaDTO, hakukohdeOid:HakukohdeOid, valintatapajonoOid:ValintatapajonoOid) = {
    hakija.getHakutoiveet.size must_== 1
    val hakutoive = hakija.getHakutoiveet.asScala.head
    hakutoive.getHakukohdeOid must_== hakukohdeOid.toString
    hakutoive.getHakutoiveenValintatapajonot.size must_== 1
    val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
    valintatapajono.getValintatapajonoOid must_== valintatapajonoOid.toString
    valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
    valintatapajono.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
    valintatapajono.getJonosija must_== null
  }

  "hakemukset (kevytHakijaDto)" should {

    "return hakijat and hakutoiveet for hakukohde (sijoittelu)" in {
      val kevytHakijaDtot = raportointiService.hakemukset(sijoitteluajo, sijoittelunHakukohdeOid1)

      kevytHakijaDtot.size must_== 8

      List("Haku2.1.1.1", "Haku2.1.1.2", "Haku2.1.2.1", "Haku2.1.2.2", "Haku2.1.3.1", "Haku2.1.3.2", "Haku2.1.4.1", "Haku2.1.4.2").diff(
        kevytHakijaDtot.map(_.getHakemusOid)
      ) must_== List()

      kevytHakijaDtot.foreach{ hakija =>
        hakija.getHakutoiveet.size must_== 1
        val hakutoive = hakija.getHakutoiveet.asScala.head
        hakutoive.getHakukohdeOid must_== sijoittelunHakukohdeOid1.toString
        hakutoive.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono.getValintatapajonoOid) must_== true
        valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono.getJonosija must_== Integer.parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))
      }
      true must_== true
    }
    "return hakijat and hakutoiveet for hakukohde (erillishaku, ei sijoittelua)" in {
      val kevytHakijaDtot = raportointiService.hakemukset(erillishaku, oidHaku1hakukohde1)

      kevytHakijaDtot.size must_== 2

      List(hakemusOid1.toString, hakemusOid2.toString).diff(kevytHakijaDtot.map(_.getHakemusOid)) must_== List()

      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid1.toString)).get, oidHaku1hakukohde1, oidHaku1hakukohde1jono1)
      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid2.toString)).get, oidHaku1hakukohde1, oidHaku1hakukohde1jono1)
    }

    step(insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, sijoittelunHakemusOid2)))
    step(insertValinnantulos(hakuOid2, valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, sijoittelunHakemusOid2)))

    "return hakijat and hakutoiveet for hakukohde (both with and without sijoittelu)" in {
      val kevytHakijaDtot = raportointiService.hakemukset(sijoitteluajo, oidHaku2hakukohde1)
      kevytHakijaDtot.size must_== 5
      List(hakemusOid5.toString, hakemusOid6.toString, hakemusOid7.toString, hakemusOid8.toString, sijoittelunHakemusOid2.toString).diff(
        kevytHakijaDtot.map(_.getHakemusOid)) must_== List()

      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid5.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono1)
      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid6.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono1)
      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid7.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono2)
      assertErillishakuHakija(kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid8.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono2)

      kevytHakijaDtot.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach{ hakija =>
        hakija.getHakutoiveet.size must_== 2
        val hakutoive1 = hakija.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString)).get
        hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive1.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
        valintatapajono1.getJonosija must_== Integer.parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))


        val hakutoive2 = hakija.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString)).get
        hakutoive2.getHakutoiveenValintatapajonot.size must_== 2
        val valintatapajono2 = hakutoive2.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString)).get
        valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
        valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        valintatapajono2.getJonosija must_== null
        val valintatapajono3 = hakutoive2.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString)).get
        valintatapajono3.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono3.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        valintatapajono3.getJonosija must_== null
      }
      true must_== true
    }
  }

  "hakemukset vain hakukohteen tietojen kanssa" should {
    "return hakijat and only one hakutoive for hakukohde (hakukohde without sijoittelu)" in {
      val kevytHakijaDtot1 = raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluajo, oidHaku2hakukohde1)
      kevytHakijaDtot1.size must_== 5
      List(hakemusOid5.toString, hakemusOid6.toString, hakemusOid7.toString, hakemusOid8.toString, sijoittelunHakemusOid2.toString).diff(
        kevytHakijaDtot1.map(_.getHakemusOid)) must_== List()

      assertErillishakuHakija(kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid5.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono1)
      assertErillishakuHakija(kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid6.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono1)
      assertErillishakuHakija(kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid7.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono2)
      assertErillishakuHakija(kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid8.toString)).get, oidHaku2hakukohde1, oidHaku2hakukohde1jono2)

      kevytHakijaDtot1.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach { hakija =>
        hakija.getHakutoiveet.size must_== 1
        val hakutoive = hakija.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString)).get
        hakutoive.getHakutoiveenValintatapajonot.size must_== 2
        val valintatapajono1 = hakutoive.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString)).get
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        valintatapajono1.getJonosija must_== null
        val valintatapajono2 = hakutoive.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString)).get
        valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        valintatapajono2.getJonosija must_== null
      }
      true must_== true
    }

    "return hakijat and only one hakutoive for hakukohde (hakukohde with sijoittelu)" in {
      val kevytHakijaDtot = raportointiService.hakemuksetVainHakukohteenTietojenKanssa(sijoitteluajo, sijoittelunHakukohdeOid2)

      kevytHakijaDtot.size must_== 8
      List("Haku2.2.1.1", "Haku2.2.1.2", "Haku2.2.2.1", "Haku2.2.2.2", "Haku2.2.3.1", "Haku2.2.3.2", "Haku2.2.4.1", "Haku2.2.4.2").diff(
        kevytHakijaDtot.map(_.getHakemusOid)
      ) must_== List()

      kevytHakijaDtot.foreach{ hakija =>
        hakija.getHakutoiveet.size must_== 1
        val hakutoive1 = hakija.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString)).get
        hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive1.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
        valintatapajono1.getJonosija must_== Integer.parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))
      }
      true must_== true
    }
  }

  step(deleteAll())
}
