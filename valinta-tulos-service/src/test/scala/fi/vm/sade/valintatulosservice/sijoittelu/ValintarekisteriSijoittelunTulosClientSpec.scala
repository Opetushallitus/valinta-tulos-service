package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.ITSetup
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriSijoittelunTulosClientSpec extends ITSpecification with ValintarekisteriTestData {
  step(deleteAll())

  lazy val client = new ValintarekisteriSijoittelunTulosClientImpl(singleConnectionValintarekisteriDb)
  step(createSijoitteluajoHaulle2)
  step(createHakujen1Ja2ValinnantuloksetIlmanSijoittelua)

  "fetch latest sijoitteluajo" should {

    "return None if sijoitteluajo not found" in {
      None must_== client.fetchLatestSijoitteluAjo(HakuOid("haku3"))
    }

    "return sijoitteluajo" in {
      val sijoitteluajo = client.fetchLatestSijoitteluAjo(hakuOid2).get
      sijoitteluajo.getSijoitteluajoId must_== sijoitteluajoId
      sijoitteluajo.getHakuOid must_== hakuOid2.toString
      sijoitteluajo.getHakukohteet.asScala.map(_.getOid).diff(List(oidHaku2hakukohde1.toString, "Haku2.1", "Haku2.2")) must_== List()
    }

    "return sijoitteluajo (erillishaku, ei sijoittelua)" in {
      val sijoitteluajo = client.fetchLatestSijoitteluAjo(hakuOid1).get
      sijoitteluajo.isInstanceOf[SyntheticSijoitteluAjoForHakusWithoutSijoittelu] must_== true
      sijoitteluajo.getSijoitteluajoId must_== SyntheticSijoitteluAjoForHakusWithoutSijoittelu.syntheticSijoitteluajoId
      sijoitteluajo.getHakuOid must_== hakuOid1.toString
      sijoitteluajo.getHakukohteet.asScala.map(_.getOid).diff(List(oidHaku1hakukohde1.toString, oidHaku1hakukohde2.toString)) must_== List()
    }

    "return None if hakukohde not found" in {
      None must_== client.fetchLatestSijoitteluAjo(hakuOid2, Some(oidHaku1hakukohde1))
    }

    "return None if hakukohde not found (erillishaku, ei sijoittelua)" in {
      None must_== client.fetchLatestSijoitteluAjo(hakuOid1, Some(oidHaku2hakukohde1))
    }

    "return latest sijoitteluajo for hakukohde" in {
      val sijoitteluajo = client.fetchLatestSijoitteluAjo(hakuOid2, Some(oidHaku2hakukohde1)).get
      sijoitteluajo.getSijoitteluajoId must_== sijoitteluajoId
      sijoitteluajo.getHakuOid must_== hakuOid2.toString
      sijoitteluajo.getHakukohteet.asScala.map(_.getOid).diff(List(oidHaku2hakukohde1.toString, "Haku2.1", "Haku2.2")) must_== List()
    }

    "return latest sijoitteluajo for hakukohde (erillishaku, ei sijoittelua)" in {
      val sijoitteluajo = client.fetchLatestSijoitteluAjo(hakuOid1, Some(oidHaku1hakukohde1)).get
      sijoitteluajo.isInstanceOf[SyntheticSijoitteluAjoForHakusWithoutSijoittelu] must_== true
      sijoitteluajo.getSijoitteluajoId must_== SyntheticSijoitteluAjoForHakusWithoutSijoittelu.syntheticSijoitteluajoId
      sijoitteluajo.getHakuOid must_== hakuOid1.toString
      sijoitteluajo.getHakukohteet.asScala.map(_.getOid).diff(List(oidHaku1hakukohde1.toString, oidHaku1hakukohde2.toString)) must_== List()
    }
  }

  "fetch hakemuksen tulos" should {

    step(insertValinnantulos(hakuOid2, valinnantulos(sijoittelunHakukohdeOid2, sijoittelunValintatapajonoOid2, sijoittelunHakemusOid1)))

    "return None if hakija not found" in {
      client.fetchHakemuksenTulos(singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.getLatestSijoitteluajoId(hakuOid2)), hakuOid2, HakemusOid("fakeHakemusOid")) must beNone
    }

    "return hakijan hakutoiveet" in {
      val hakijaDto = client.fetchHakemuksenTulos(singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.getLatestSijoitteluajoId(hakuOid2)), hakuOid2, sijoittelunHakemusOid1).get
      hakijaDto.getHakijaOid must_== sijoittelunHakemusOid1.toString
      hakijaDto.getHakemusOid must_== sijoittelunHakemusOid1.toString

      hakijaDto.getHakutoiveet.asScala.size must_== 1
      val hakutoive = hakijaDto.getHakutoiveet.asScala.head
      hakutoive.getHakukohdeOid must_== sijoittelunHakukohdeOid2.toString
      hakutoive.getVastaanottotieto must_== fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI

      hakutoive.getHakutoiveenValintatapajonot.asScala.size must_== 1
      val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
      valintatapajono.getHakeneet must_== 2
      valintatapajono.getHyvaksytty must_== 1
      valintatapajono.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
      valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
      valintatapajono.getValintatapajonoOid must_== sijoittelunValintatapajonoOid2.toString
    }

    "return hakijan hakutoiveet (erillishaku, ei sijoittelua)" in {
      val hakijaDto = client.fetchHakemuksenTulos(singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.getLatestSijoitteluajoId(hakuOid1)), hakuOid1, hakemusOid2).get
      hakijaDto.getHakijaOid must_== hakemusOid2.toString
      hakijaDto.getHakemusOid must_== hakemusOid2.toString

      hakijaDto.getHakutoiveet.asScala.size must_== 1
      val hakutoive = hakijaDto.getHakutoiveet.asScala.head
      hakutoive.getHakukohdeOid must_== oidHaku1hakukohde1.toString
      hakutoive.getVastaanottotieto must_== fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI

      hakutoive.getHakutoiveenValintatapajonot.asScala.size must_== 1
      val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
      valintatapajono.getHakeneet must_== 2
      valintatapajono.getHyvaksytty must_== 2
      valintatapajono.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
      valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
      valintatapajono.getValintatapajonoOid must_== oidHaku1hakukohde1jono1.toString
    }

    step(insertValinnantulos(hakuOid2, valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, sijoittelunHakemusOid2)))
    step(insertValinnantulos(hakuOid2, valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, sijoittelunHakemusOid2)))

    "return hakijan hakutoiveet (both with and without sijoittelu)" in {
      val hakijaDto = client.fetchHakemuksenTulos(singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.getLatestSijoitteluajoId(hakuOid2)), hakuOid2, sijoittelunHakemusOid2).get
      hakijaDto.getHakijaOid must_== sijoittelunHakemusOid2.toString
      hakijaDto.getHakemusOid must_== sijoittelunHakemusOid2.toString

      hakijaDto.getHakutoiveet.asScala.size must_== 2
      val hakutoive1 = hakijaDto.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString)).get
      hakutoive1.getVastaanottotieto must_== fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI

      val hakutoive2 = hakijaDto.getHakutoiveet.asScala.find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString)).get
      hakutoive2.getVastaanottotieto must_== fi.vm.sade.sijoittelu.tulos.dto.ValintatuloksenTila.KESKEN

      hakutoive1.getHakutoiveenValintatapajonot.asScala.size must_== 2
      val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString)).get
      valintatapajono1.getHakeneet must_== 3
      valintatapajono1.getHyvaksytty must_== 3
      valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
      valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY

      val valintatapajono2 = hakutoive1.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString)).get
      valintatapajono2.getHakeneet must_== 3
      valintatapajono2.getHyvaksytty must_== 2
      valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
      valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY

      hakutoive2.getHakutoiveenValintatapajonot.asScala.size must_== 1
      val valintatapajono3 = hakutoive2.getHakutoiveenValintatapajonot.asScala.find(_.getValintatapajonoOid.equals(sijoittelunValintatapajonoOid2.toString)).get
      valintatapajono3.getHakeneet must_== 2
      valintatapajono3.getHyvaksytty must_== 1
      valintatapajono3.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
      valintatapajono3.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
    }
  }

  step(deleteAll())
}
