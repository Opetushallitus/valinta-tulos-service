package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import fi.vm.sade.valintatulosservice.valintarekisteri.ITSetup
import org.specs2.mutable.Specification
import scala.collection.JavaConverters._

class ValintarekisteriSijoittelunTulosClientSpec extends Specification with ITSetup with ValintarekisteriTestData {
  sequential
  step(appConfig.start)
  step(deleteAll())

  lazy val client = new ValintarekisteriSijoittelunTulosClientImpl(singleConnectionValintarekisteriDb, singleConnectionValintarekisteriDb)

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
}
