package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dto.{SijoitteluDTO, SijoitteluajoDTO}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach

import scala.collection.JavaConverters._
import scala.util.Try


@RunWith(classOf[JUnitRunner])
class ValintarekisteriForSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach with Logging with PerformanceLogger {
  sequential
  step(appConfig.start)
  step(deleteAll())

  lazy val valintarekisteri = new ValintarekisteriService(singleConnectionValintarekisteriDb, hakukohdeRecordService)

  def getLatestSijoittelu(hakuOid: String) = {
    val sijoitteluajo = time("Get latest sijoitteluajo") { valintarekisteri.getLatestSijoitteluajo(hakuOid) }
    val hakukohteet = time("Get hakukohteet") { valintarekisteri.getSijoitteluajonHakukohteet(sijoitteluajo.getSijoitteluajoId) }
    (sijoitteluajo, hakukohteet)
  }

  def getSijoittelu(hakuOid: String, sijoitteluajoId:String) = {
    val sijoitteluajo = time("Get sijoitteluajo") { valintarekisteri.getSijoitteluajo(hakuOid, sijoitteluajoId) }
    val hakukohteet = time("Get hakukohteet") { valintarekisteri.getSijoitteluajonHakukohteet(sijoitteluajo.getSijoitteluajoId) }
    (sijoitteluajo, hakukohteet)
  }

  "SijoitteluajoDTO with sijoitteluajoId should be fetched from database" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }
    val (sijoitteluajo, hakukohteet) = getSijoittelu("1.2.246.562.29.75203638285", "1476936450191")
    compareSijoitteluWrapperToEntity(wrapper, sijoitteluajo, hakukohteet)
  }
  "SijoitteluajoDTO with latest sijoitteluajoId should be fetched from database" in {
    val wrapper = loadSijoitteluFromFixture("valintatapajono_hakijaryhma_pistetiedot", "sijoittelu/")
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }
    val (sijoitteluajo, hakukohteet) = getLatestSijoittelu("korkeakoulu-erillishaku")
    compareSijoitteluWrapperToEntity(wrapper, sijoitteluajo, hakukohteet)
  }
  "Sijoittelu and hakukohteet should be saved in database 1" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    time("Tallenna sijoittelu") { valintarekisteri.tallennaSijoittelu(wrapper.sijoitteluajo, wrapper.hakukohteet.asJava, wrapper.valintatulokset.asJava) }
    assertSijoittelu(wrapper)
  }
  "Sijoittelu and hakukohteet should be saved in database 2" in {
    val wrapper = loadSijoitteluFromFixture("valintatapajono_hakijaryhma_pistetiedot", "sijoittelu/")
    time("Tallenna sijoittelu") { valintarekisteri.tallennaSijoittelu(wrapper.sijoitteluajo, wrapper.hakukohteet.asJava, wrapper.valintatulokset.asJava) }
    assertSijoittelu(wrapper)
  }
  "Sijoitteluajo should be stored in transaction" in {
    val wrapper = loadSijoitteluFromFixture("valintatapajono_hakijaryhma_pistetiedot", "sijoittelu/")
    wrapper.hakukohteet(0).getValintatapajonot.get(0).getHakemukset.get(0).setHakemusOid(null)
    (valintarekisteri.tallennaSijoittelu(wrapper.sijoitteluajo, wrapper.hakukohteet.asJava, wrapper.valintatulokset.asJava) must throwA[Exception]).message must contain("null value in column \"hakemus_oid\" violates not-null constraint")
    findSijoitteluajo(wrapper.sijoitteluajo.getSijoitteluajoId) mustEqual None
  }
  "Unknown sijoitteluajo cannot be found" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
    valintarekisteri.getSijoitteluajo("1.2.246.562.29.75203638285", "1476936450192") must throwA(
      new IllegalArgumentException(s"Sijoitteluajoa 1476936450192 ei löytynyt haulle 1.2.246.562.29.75203638285"))
  }
  "Exception is thrown when no latest sijoitteluajo is found" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
    valintarekisteri.getLatestSijoitteluajo("1.2.246.562.29.75203638286") must throwA(
      new NotFoundException("Yhtään sijoitteluajoa ei löytynyt haulle 1.2.246.562.29.75203638286"))
  }
  "Exception is thrown when sijoitteluajoId is malformed" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
    valintarekisteri.getSijoitteluajo("1.2.246.562.29.75203638285", "1476936450192a") must throwA(
      new IllegalArgumentException("Väärän tyyppinen sijoitteluajon ID: 1476936450192a"))
  }
  "Tilahistoria is created correctly" in {
    val hakukohdeOid = "1.2.246.562.20.56217166919"
    val valintatapajonoOid = "14539780970882907815262745035155"
    val hakemusOid = "1.2.246.562.11.00006534907"

    val sijoitteluajo1Ajat = (1180406134614l, 1180506134614l)
    val sijoitteluajo2Ajat = (1280406134614l, 1280506134614l)
    val sijoitteluajo3Ajat = (1380406134614l, 1380506134614l)
    val sijoitteluajo4Ajat = (1480406134614l, 1480506134614l)
    val sijoitteluajo5Ajat = (1580406134614l, 1580506134614l)

    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")

    updateSijoitteluajo(123456l, sijoitteluajo1Ajat, wrapper)
    updateTila(HakemuksenTila.HYVAKSYTTY, sijoitteluajo1Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    updateSijoitteluajo(223456l, sijoitteluajo2Ajat, wrapper)
    updateTila(HakemuksenTila.VARALLA, sijoitteluajo2Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    updateSijoitteluajo(333456l, sijoitteluajo3Ajat, wrapper)
    updateTila(HakemuksenTila.VARASIJALTA_HYVAKSYTTY, sijoitteluajo3Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    updateSijoitteluajo(444456l, sijoitteluajo4Ajat, wrapper)
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    updateSijoitteluajo(555556l, sijoitteluajo5Ajat, wrapper)
    updateTila(HakemuksenTila.VARALLA, sijoitteluajo5Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    val (_, tallennettuSijoitteluajo) = getSijoittelu("1.2.246.562.29.75203638285", "latest")

    val tallennettuTilahistoria = findTilahistoria(tallennettuSijoitteluajo, hakukohdeOid, valintatapajonoOid, hakemusOid)

    tallennettuTilahistoria.size mustEqual 4
    tallennettuTilahistoria.get(3).getLuotu.getTime mustEqual sijoitteluajo5Ajat._1
    tallennettuTilahistoria.get(3).getTila mustEqual HakemuksenTila.VARALLA
    tallennettuTilahistoria.get(2).getLuotu.getTime mustEqual sijoitteluajo3Ajat._1
    tallennettuTilahistoria.get(2).getTila mustEqual HakemuksenTila.VARASIJALTA_HYVAKSYTTY
    tallennettuTilahistoria.get(1).getLuotu.getTime mustEqual sijoitteluajo2Ajat._1
    tallennettuTilahistoria.get(1).getTila mustEqual HakemuksenTila.VARALLA
    tallennettuTilahistoria.get(0).getLuotu.getTime mustEqual sijoitteluajo1Ajat._1
    tallennettuTilahistoria.get(0).getTila mustEqual HakemuksenTila.HYVAKSYTTY

    val (_, aikaisempiSijoitteluajo) = getSijoittelu("1.2.246.562.29.75203638285", "333456")

    val aikaisemminTallennetunTilahistoria = findTilahistoria(aikaisempiSijoitteluajo, hakukohdeOid, valintatapajonoOid,hakemusOid)

    aikaisemminTallennetunTilahistoria.size mustEqual 3
    aikaisemminTallennetunTilahistoria.get(2).getLuotu.getTime mustEqual sijoitteluajo3Ajat._1
    aikaisemminTallennetunTilahistoria.get(2).getTila mustEqual HakemuksenTila.VARASIJALTA_HYVAKSYTTY
    aikaisemminTallennetunTilahistoria.get(1).getLuotu.getTime mustEqual sijoitteluajo2Ajat._1
    aikaisemminTallennetunTilahistoria.get(1).getTila mustEqual HakemuksenTila.VARALLA
    aikaisemminTallennetunTilahistoria.get(0).getLuotu.getTime mustEqual sijoitteluajo1Ajat._1
    aikaisemminTallennetunTilahistoria.get(0).getTila mustEqual HakemuksenTila.HYVAKSYTTY
  }
  "Tilahistoria is read for hakemukset that have not changed in requested sijoitteluajo" in {
    val hakukohdeOid = "1.2.246.562.20.56217166919"
    val valintatapajonoOid = "14539780970882907815262745035155"
    val hakemusOid1 = "1.2.246.562.11.00006550693"
    val hakemusOid2 = "1.2.246.562.11.00006654858"

    val sijoitteluajo1Ajat = (1180406134614l, 1180506134614l)
    val sijoitteluajo2Ajat = (1280406134614l, 1280506134614l)

    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")

    updateSijoitteluajo(123456l, sijoitteluajo1Ajat, wrapper)
    updateTila(HakemuksenTila.HYVAKSYTTY, sijoitteluajo1Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid1))
    updateTila(HakemuksenTila.HYVAKSYTTY, sijoitteluajo1Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid2))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    updateSijoitteluajo(223456l, sijoitteluajo2Ajat, wrapper)
    updateTila(HakemuksenTila.VARALLA, sijoitteluajo2Ajat._1, findHakemus(wrapper, hakukohdeOid, valintatapajonoOid, hakemusOid1))
    time("Store sijoittelu") { singleConnectionValintarekisteriDb.storeSijoittelu(wrapper) }

    val (_, ensimmainen) = getSijoittelu("1.2.246.562.29.75203638285", "123456")
    val (_, toinen) = getSijoittelu("1.2.246.562.29.75203638285", "latest")

    findTilahistoria(ensimmainen, hakukohdeOid, valintatapajonoOid, hakemusOid1).size mustEqual 1
    findTilahistoria(ensimmainen, hakukohdeOid, valintatapajonoOid, hakemusOid2).size mustEqual 1
    findTilahistoria(toinen, hakukohdeOid, valintatapajonoOid, hakemusOid1).size mustEqual 2
    findTilahistoria(toinen, hakukohdeOid, valintatapajonoOid, hakemusOid2).size mustEqual 1
  }
  "Poista hakijaryhmät, joiden jonoa ei ole sijoiteltu" in {
    def createHakijaryhma(oid:String, valintatapajonoOid:String) = {
      val hakijaryhma:Hakijaryhma = new Hakijaryhma()
      hakijaryhma.setOid(oid)
      hakijaryhma.setValintatapajonoOid(valintatapajonoOid)
      hakijaryhma
    }

    def createValintatapajono(oid:String) = {
      val valintatapajono:Valintatapajono = new Valintatapajono()
      valintatapajono.setOid(oid)
      valintatapajono
    }

    def createHakukohde(oid:String, valintatapajonot:java.util.List[Valintatapajono], hakijaryhmat:java.util.List[Hakijaryhma]) = {
      val hakukohde:Hakukohde = new Hakukohde()
      hakukohde.setOid(oid)
      hakukohde.setValintatapajonot(valintatapajonot)
      hakukohde.setHakijaryhmat(hakijaryhmat)
      hakukohde
    }

    import scala.collection.JavaConverters._

    val hakukohteet = List(
      createHakukohde("h1",
        List(createValintatapajono("v1"), createValintatapajono("v2"), createValintatapajono("v3")).asJava,
        List(createHakijaryhma("r1", "v1"), createHakijaryhma("r2", null), createHakijaryhma("r3", "puuttuva")).asJava),
      createHakukohde("h2",
        List(createValintatapajono("v1"), createValintatapajono("v2"), createValintatapajono("v3")).asJava,
        List(createHakijaryhma("r1", "v1"), createHakijaryhma("r2", "v2"), createHakijaryhma("r3", "v3")).asJava),
      createHakukohde("h3",
        List(createValintatapajono("v1"), createValintatapajono("v2"), createValintatapajono("v3")).asJava,
        List(createHakijaryhma("r1", null), createHakijaryhma("r2", "puuttuva"), createHakijaryhma("r3", "puuttuva")).asJava)
    )

    Valintarekisteri.poistaValintatapajonokohtaisetHakijaryhmatJoidenJonoaEiSijoiteltu(hakukohteet.asJava)

    hakukohteet.size mustEqual 3
    hakukohteet.map(_.getOid).diff(List("h1", "h2", "h3")) mustEqual List()
    hakukohteet.find(_.getOid == "h1").get.getHakijaryhmat.size mustEqual 2
    hakukohteet.find(_.getOid == "h2").get.getHakijaryhmat.size mustEqual 3
    hakukohteet.find(_.getOid == "h3").get.getHakijaryhmat.size mustEqual 1
    hakukohteet.find(_.getOid == "h1").get.getHakijaryhmat.asScala.map(_.getOid).diff(List("r1", "r2")) mustEqual List()
    hakukohteet.find(_.getOid == "h2").get.getHakijaryhmat.asScala.map(_.getOid).diff(List("r1", "r2", "r3")) mustEqual List()
    hakukohteet.find(_.getOid == "h3").get.getHakijaryhmat.asScala.map(_.getOid).diff(List("r1")) mustEqual List()
  }

  override protected def before: Unit = {
    deleteAll()
  }
  override protected def after: Unit = {
    deleteAll()
  }

  step(deleteAll())

  private def updateTila(tila:HakemuksenTila, aika:Long, hakemus:Hakemus) = {
    hakemus.setTila(tila)
    val newTilahistoria = new java.util.ArrayList(hakemus.getTilaHistoria)
    val valinnantila = Valinnantila(tila)
    newTilahistoria.add(TilahistoriaWrapper(valinnantila, new java.util.Date(aika)).tilahistoria)
    hakemus.setTilaHistoria(newTilahistoria)
  }

  private def findHakemus(wrapper:SijoitteluWrapper, hakukohdeOid: String, valintatapajonoOid: String, hakemusOid: String): Hakemus = {
    wrapper.hakukohteet.find(_.getOid.equals(hakukohdeOid)).get.getValintatapajonot.asScala.find(
      _.getOid.equals(valintatapajonoOid)).get.getHakemukset.asScala.find(_.getHakemusOid.equals(hakemusOid)).get
  }

  private def findTilahistoria(sijoitteluajo: SijoitteluajoDTO, hakukohdeOid: String, valintatapajonoOid: String, hakemusOid: String) = {
    sijoitteluajo.getHakukohteet.asScala.find(_.getOid.equals(hakukohdeOid)).get
      .getValintatapajonot.asScala.find(_.getOid.equals(valintatapajonoOid)).get
      .getHakemukset.asScala.find(_.getHakemusOid.equals(hakemusOid)).get.getTilaHistoria
  }

  private def findTilahistoria(hakukohteet:java.util.List[Hakukohde], hakukohdeOid: String, valintatapajonoOid: String, hakemusOid: String) = {
    hakukohteet.asScala.find(_.getOid.equals(hakukohdeOid)).get
      .getValintatapajonot.asScala.find(_.getOid.equals(valintatapajonoOid)).get
      .getHakemukset.asScala.find(_.getHakemusOid.equals(hakemusOid)).get.getTilaHistoria
  }

  private def updateSijoitteluajo(sijoitteluajoId:Long, ajat:(Long,Long), wrapper:SijoitteluWrapper) = {
    wrapper.sijoitteluajo.setSijoitteluajoId(sijoitteluajoId)
    wrapper.sijoitteluajo.setStartMils(ajat._1)
    wrapper.hakukohteet.foreach(_.setSijoitteluajoId(sijoitteluajoId))
    wrapper.sijoitteluajo.setEndMils(ajat._2)
  }
}
