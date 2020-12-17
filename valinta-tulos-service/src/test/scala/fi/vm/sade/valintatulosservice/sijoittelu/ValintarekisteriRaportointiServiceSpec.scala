package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{
  HakijaDTO,
  HakijaPaginationObject,
  KevytHakijaDTO
}
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakemusOid,
  HakuOid,
  HakukohdeOid,
  ValintatapajonoOid
}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriRaportointiServiceSpec extends ITSpecification with ValintarekisteriTestData {
  step(deleteAll())

  lazy val raportointiService = new ValintarekisteriRaportointiServiceImpl(
    singleConnectionValintarekisteriDb,
    new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb)
  )
  lazy val client = new ValintarekisteriSijoittelunTulosClientImpl(
    singleConnectionValintarekisteriDb
  )

  step(createSijoitteluajoHaulle2)
  step(createHakujen1Ja2ValinnantuloksetIlmanSijoittelua)

  lazy val sijoitteluajo = raportointiService.getSijoitteluAjo(sijoitteluajoId).get
  lazy val erillishaku = client.fetchLatestSijoitteluAjo(hakuOid1).get

  def assertErillishakuKevytHakija(
    hakija: KevytHakijaDTO,
    hakukohdeOid: HakukohdeOid,
    valintatapajonoOid: ValintatapajonoOid
  ) = {
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

  def assertErillishakuHakija(
    hakija: HakijaDTO,
    hakukohdeOid: HakukohdeOid,
    valintatapajonoOid: ValintatapajonoOid,
    hyvaksytty: Int
  ) = {
    hakija.getHakutoiveet.size must_== 1
    val hakutoive = hakija.getHakutoiveet.asScala.head
    hakutoive.getHakukohdeOid must_== hakukohdeOid.toString
    hakutoive.getHakutoiveenValintatapajonot.size must_== 1
    val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
    valintatapajono.getValintatapajonoOid must_== valintatapajonoOid.toString
    valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
    valintatapajono.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
    valintatapajono.getHakeneet must_== 3
    valintatapajono.getHyvaksytty must_== hyvaksytty
  }

  "hakemukset (kevytHakijaDto)" should {

    "return hakijat and hakutoiveet for hakukohde (sijoittelu)" in {
      val kevytHakijaDtot =
        raportointiService.kevytHakemukset(sijoitteluajo, sijoittelunHakukohdeOid1)

      kevytHakijaDtot.size must_== 8

      List(
        "Haku2.1.1.1",
        "Haku2.1.1.2",
        "Haku2.1.2.1",
        "Haku2.1.2.2",
        "Haku2.1.3.1",
        "Haku2.1.3.2",
        "Haku2.1.4.1",
        "Haku2.1.4.2"
      ).diff(
        kevytHakijaDtot.map(_.getHakemusOid)
      ) must_== List()

      kevytHakijaDtot.foreach { hakija =>
        hakija.getHakutoiveet.size must_== 1
        val hakutoive = hakija.getHakutoiveet.asScala.head
        hakutoive.getHakukohdeOid must_== sijoittelunHakukohdeOid1.toString
        hakutoive.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono = hakutoive.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono.getValintatapajonoOid) must_== true
        valintatapajono.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono.getJonosija must_== Integer.parseInt(
          "" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1)
        )
      }
      true must_== true
    }
    "return hakijat and hakutoiveet for hakukohde (erillishaku, ei sijoittelua)" in {
      val kevytHakijaDtot = raportointiService.kevytHakemukset(erillishaku, oidHaku1hakukohde1)

      kevytHakijaDtot.size must_== 2

      List(hakemusOid1.toString, hakemusOid2.toString).diff(
        kevytHakijaDtot.map(_.getHakemusOid)
      ) must_== List()

      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid1.toString)).get,
        oidHaku1hakukohde1,
        oidHaku1hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid2.toString)).get,
        oidHaku1hakukohde1,
        oidHaku1hakukohde1jono1
      )
    }

    step(
      insertValinnantulos(
        hakuOid2,
        valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, sijoittelunHakemusOid2)
      )
    )
    step(
      insertValinnantulos(
        hakuOid2,
        valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, sijoittelunHakemusOid2)
      )
    )

    "return hakijat and hakutoiveet for hakukohde (both with and without sijoittelu)" in {
      val kevytHakijaDtot = raportointiService.kevytHakemukset(sijoitteluajo, oidHaku2hakukohde1)
      kevytHakijaDtot.size must_== 5
      List(
        hakemusOid5.toString,
        hakemusOid6.toString,
        hakemusOid7.toString,
        hakemusOid8.toString,
        sijoittelunHakemusOid2.toString
      ).diff(kevytHakijaDtot.map(_.getHakemusOid)) must_== List()

      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid5.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid6.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid7.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot.find(_.getHakemusOid.equals(hakemusOid8.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )

      kevytHakijaDtot.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach {
        hakija =>
          hakija.getHakutoiveet.size must_== 2
          val hakutoive1 = hakija.getHakutoiveet.asScala
            .find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString))
            .get
          hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
          hakutoive1.isKaikkiJonotSijoiteltu must_== true
          val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
          hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
          valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
          valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
          valintatapajono1.getJonosija must_== Integer
            .parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))

          val hakutoive2 = hakija.getHakutoiveet.asScala
            .find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString))
            .get
          hakutoive2.getHakutoiveenValintatapajonot.size must_== 2
          val valintatapajono2 = hakutoive2.getHakutoiveenValintatapajonot.asScala
            .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString))
            .get
          valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
          valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
          valintatapajono2.getJonosija must_== null
          val valintatapajono3 = hakutoive2.getHakutoiveenValintatapajonot.asScala
            .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString))
            .get
          valintatapajono3.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
          valintatapajono3.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
          valintatapajono3.getJonosija must_== null
      }
      true must_== true
    }
  }

  "hakemukset vain hakukohteen tietojen kanssa" should {
    "return hakijat and only one hakutoive for hakukohde (hakukohde without sijoittelu)" in {
      val kevytHakijaDtot1 = raportointiService.hakemuksetVainHakukohteenTietojenKanssa(
        sijoitteluajo,
        oidHaku2hakukohde1
      )
      kevytHakijaDtot1.size must_== 5
      List(
        hakemusOid5.toString,
        hakemusOid6.toString,
        hakemusOid7.toString,
        hakemusOid8.toString,
        sijoittelunHakemusOid2.toString
      ).diff(kevytHakijaDtot1.map(_.getHakemusOid)) must_== List()

      assertErillishakuKevytHakija(
        kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid5.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid6.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid7.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )
      assertErillishakuKevytHakija(
        kevytHakijaDtot1.find(_.getHakemusOid.equals(hakemusOid8.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )

      kevytHakijaDtot1.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach {
        hakija =>
          hakija.getHakutoiveet.size must_== 1
          val hakutoive = hakija.getHakutoiveet.asScala
            .find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString))
            .get
          hakutoive.getHakutoiveenValintatapajonot.size must_== 2
          val valintatapajono1 = hakutoive.getHakutoiveenValintatapajonot.asScala
            .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString))
            .get
          valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
          valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
          valintatapajono1.getJonosija must_== null
          val valintatapajono2 = hakutoive.getHakutoiveenValintatapajonot.asScala
            .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString))
            .get
          valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
          valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
          valintatapajono2.getJonosija must_== null
      }
      true must_== true
    }

    "return hakijat and only one hakutoive for hakukohde (hakukohde with sijoittelu)" in {
      val kevytHakijaDtot = raportointiService.hakemuksetVainHakukohteenTietojenKanssa(
        sijoitteluajo,
        sijoittelunHakukohdeOid2
      )

      kevytHakijaDtot.size must_== 8
      List(
        "Haku2.2.1.1",
        "Haku2.2.1.2",
        "Haku2.2.2.1",
        "Haku2.2.2.2",
        "Haku2.2.3.1",
        "Haku2.2.3.2",
        "Haku2.2.4.1",
        "Haku2.2.4.2"
      ).diff(
        kevytHakijaDtot.map(_.getHakemusOid)
      ) must_== List()

      kevytHakijaDtot.foreach { hakija =>
        hakija.getHakutoiveet.size must_== 1
        val hakutoive1 = hakija.getHakutoiveet.asScala
          .find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString))
          .get
        hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive1.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
        valintatapajono1.getJonosija must_== Integer.parseInt(
          "" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1)
        )
      }
      true must_== true
    }
  }

  "hakemukset (HakijaPaginationObject)" should {
    "return hakijat and hakutoiveet for haku (both with and without sijoittelu)" in {
      val paginationObject = raportointiService.hakemukset(sijoitteluajo)
      paginationObject.getTotalCount must_== 20

      val hakijaDtot = paginationObject.getResults.asScala
      hakijaDtot.size must_== 20

      assertErillishakuHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid5.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1,
        3
      )
      assertErillishakuHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid6.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1,
        3
      )
      assertErillishakuHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid7.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2,
        2
      )
      assertErillishakuHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid8.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2,
        2
      )

      hakijaDtot.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach { hakija =>
        hakija.getHakutoiveet.size must_== 2
        val hakutoive1 = hakija.getHakutoiveet.asScala
          .find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString))
          .get
        hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive1.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
        valintatapajono1.getJonosija must_== Integer
          .parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))
        valintatapajono1.getHakeneet must_== 2
        valintatapajono1.getHyvaksytty must_== 0

        val hakutoive2 = hakija.getHakutoiveet.asScala
          .find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString))
          .get
        hakutoive2.getHakutoiveenValintatapajonot.size must_== 2
        val valintatapajono2 = hakutoive2.getHakutoiveenValintatapajonot.asScala
          .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString))
          .get
        valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
        valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        valintatapajono2.getHakeneet must_== 3
        valintatapajono2.getHyvaksytty must_== 3
        //valintatapajono2.getJonosija must_== null
        val valintatapajono3 = hakutoive2.getHakutoiveenValintatapajonot.asScala
          .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString))
          .get
        valintatapajono3.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono3.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
        //valintatapajono3.getJonosija must_== null
        valintatapajono3.getHakeneet must_== 3
        valintatapajono3.getHyvaksytty must_== 2
      }
      true must_== true
    }
    "paginate result" in {
      def assert(count: Int, hakemukset: List[HakemusOid], po: HakijaPaginationObject) = {
        po.getTotalCount must_== count
        val result = po.getResults.asScala
        println(result.map(r => HakemusOid(r.getHakemusOid)))
        result.size must_== hakemukset.size
        result.map(r => HakemusOid(r.getHakemusOid)).diff(hakemukset) must_== List()
      }

      val sijoitteluajoId: Option[Long] = Option(sijoitteluajo.getSijoitteluajoId)
      val hakuOid = HakuOid(sijoitteluajo.getHakuOid)
      assert(
        20,
        List(HakemusOid("Haku2.1.1.1"), HakemusOid("Haku2.1.1.2")),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          None,
          None,
          None,
          None,
          Some(2),
          None
        )
      )
      assert(
        20,
        List(HakemusOid("Haku2.1.1.2"), HakemusOid("Haku2.1.2.1"), HakemusOid("Haku2.1.2.2")),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          None,
          None,
          None,
          None,
          Some(3),
          Some(1)
        )
      )
      assert(
        5,
        List(sijoittelunHakemusOid2, hakemusOid5),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          None,
          None,
          None,
          Some(List(oidHaku2hakukohde1)),
          Some(2),
          None
        )
      )
      assert(
        5,
        List(hakemusOid5, hakemusOid6),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          Some(true),
          None,
          None,
          None,
          Some(2),
          Some(1)
        )
      )
      assert(
        15,
        List(HakemusOid("Haku2.1.1.1"), HakemusOid("Haku2.1.1.2")),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          None,
          Some(true),
          None,
          None,
          Some(2),
          None
        )
      )
      assert(
        5,
        List(hakemusOid7, hakemusOid8),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          None,
          None,
          Some(true),
          None,
          Some(2),
          Some(3)
        )
      )
      assert(
        0,
        List(),
        raportointiService.hakemukset(
          sijoitteluajoId,
          hakuOid,
          Some(true),
          None,
          None,
          Some(List(sijoittelunHakukohdeOid1)),
          Some(2),
          None
        )
      )
      true must_== true
    }
  }

  "hakemukset (List[KevytHakijaDTO])" should {
    "return hakijat and hakutoiveet for haku (both with and without sijoittelu)" in {
      val hakijaDtot = raportointiService.kevytHakemukset(sijoitteluajo)
      hakijaDtot.size must_== 20

      assertErillishakuKevytHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid5.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid6.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono1
      )
      assertErillishakuKevytHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid7.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )
      assertErillishakuKevytHakija(
        hakijaDtot.find(_.getHakemusOid.equals(hakemusOid8.toString)).get,
        oidHaku2hakukohde1,
        oidHaku2hakukohde1jono2
      )

      hakijaDtot.find(_.getHakemusOid.equals(sijoittelunHakemusOid2.toString)).foreach { hakija =>
        hakija.getHakutoiveet.size must_== 2
        val hakutoive1 = hakija.getHakutoiveet.asScala
          .find(_.getHakukohdeOid.equals(sijoittelunHakukohdeOid2.toString))
          .get
        hakutoive1.getHakutoiveenValintatapajonot.size must_== 1
        hakutoive1.isKaikkiJonotSijoiteltu must_== true
        val valintatapajono1 = hakutoive1.getHakutoiveenValintatapajonot.asScala.head
        hakija.getHakemusOid.startsWith(valintatapajono1.getValintatapajonoOid) must_== true
        valintatapajono1.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono1.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.EI_TEHTY
        valintatapajono1.getJonosija must_== Integer
          .parseInt("" + hakija.getHakijaOid.charAt(hakija.getHakijaOid.length - 1))

        val hakutoive2 = hakija.getHakutoiveet.asScala
          .find(_.getHakukohdeOid.equals(oidHaku2hakukohde1.toString))
          .get
        hakutoive2.getHakutoiveenValintatapajonot.size must_== 2
        val valintatapajono2 = hakutoive2.getHakutoiveenValintatapajonot.asScala
          .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono1.toString))
          .get
        valintatapajono2.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYVAKSYTTY
        valintatapajono2.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA

        val valintatapajono3 = hakutoive2.getHakutoiveenValintatapajonot.asScala
          .find(_.getValintatapajonoOid.equals(oidHaku2hakukohde1jono2.toString))
          .get
        valintatapajono3.getTila must_== fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila.HYLATTY
        valintatapajono3.getIlmoittautumisTila must_== fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila.LASNA
      }
      true must_== true
    }
  }

  step(deleteAll())
}
