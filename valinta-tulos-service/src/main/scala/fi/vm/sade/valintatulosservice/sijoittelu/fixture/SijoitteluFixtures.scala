package fi.vm.sade.valintatulosservice.sijoittelu.fixture

import fi.vm.sade.valintatulosservice.json4sCustomFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.{DefaultFormats, JValue}
import org.json4s.JsonAST.JArray
import org.json4s.jackson.JsonMethods._
import org.springframework.core.io.ClassPathResource
import slick.driver.PostgresDriver.api.{actionBasedSQLInterpolation, _}

case class SijoitteluFixtures(valintarekisteriDb: ValintarekisteriDb) extends json4sCustomFormats {

  implicit val formats = DefaultFormats ++ List(
    new NumberLongSerializer) ++ Oids.getSerializers()

  def importFixture(fixtureName: String,
                    clear: Boolean = false,
                    yhdenPaikanSaantoVoimassa: Boolean = false,
                    kktutkintoonJohtava: Boolean = false) {
    if (clear) {
      clearFixtures()
    }
    //val tulokset = MongoMockData.readJson("fixtures/sijoittelu/" + fixtureName)
    //MongoMockData.insertData(db, tulokset)

    importJsonFixturesToPostgres(fixtureName, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava)

  }

  private def importJsonFixturesToPostgres(fixtureName: String,
                                           yhdenPaikanSaantoVoimassa: Boolean = false,
                                           kktutkintoonJohtava: Boolean = false): Unit = {

    val json = parse(scala.io.Source.fromInputStream(new ClassPathResource("fixtures/sijoittelu/" + fixtureName).getInputStream).mkString)
    SijoitteluWrapper.fromJson(json) match {
      case Some(wrapper) =>
        wrapper.hakukohteet.foreach(h => storeHakukohde(h.getOid, wrapper.sijoitteluajo.getHakuOid, wrapper.sijoitteluajo.getSijoitteluajoId, h.isKaikkiJonotSijoiteltu, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava))
        valintarekisteriDb.storeSijoittelu(wrapper)
        storeVastaanotot(json)
      case None => 
    }
  }

  private def storeVastaanotot(json: JValue) = {
    val JArray(valintatulokset) = (json \ "Valintatulos")

    for (valintatulos <- valintatulokset) {
      val tilaOption = (valintatulos \ "tila").extractOpt[String]
      tilaOption match {
        case None =>
        // pass
        case Some(tila) =>
          getVastaanottoAction(tila).foreach(action => {
            valintarekisteriDb.store(VirkailijanVastaanotto(
              (valintatulos \ "hakuOid").extract[HakuOid],
              (valintatulos \ "valintatapajonoOid").extract[ValintatapajonoOid],
              (valintatulos \ "hakijaOid").extract[String],
              (valintatulos \ "hakemusOid").extract[HakemusOid],
              (valintatulos \ "hakukohdeOid").extract[HakukohdeOid],
              action,
              (valintatulos \ "hakijaOid").extract[String],
              "Tuotu vanhasta järjestelmästä"
            ))
          })
      }
    }
  }

  private def storeHakukohde(hakukohdeOid: String, hakuOid: String, sijoitteluajoId: Long, kaikkiJonotSijoiteltu: Boolean, yhdenPaikanSaantoVoimassa: Boolean, kktutkintoonJohtava: Boolean) = {
    valintarekisteriDb.storeHakukohde(HakukohdeRecord(HakukohdeOid(hakukohdeOid), HakuOid(hakuOid), yhdenPaikanSaantoVoimassa, kktutkintoonJohtava, Kevat(2016)))
  }

  private val deleteFromVastaanotot = DBIO.seq(
    sqlu"TRUNCATE TABLE vastaanotot CASCADE",
    sqlu"TRUNCATE TABLE deleted_vastaanotot CASCADE",
    sqlu"TRUNCATE TABLE henkiloviitteet CASCADE",
    sqlu"TRUNCATE TABLE vanhat_vastaanotot CASCADE")

  private def deleteAll(): Unit = {
    valintarekisteriDb.runBlocking(DBIO.seq(
      deleteFromVastaanotot,
      sqlu"TRUNCATE TABLE valinnantilan_kuvaukset CASCADE",
      sqlu"TRUNCATE TABLE hakijaryhman_hakemukset CASCADE",
      sqlu"TRUNCATE TABLE hakijaryhmat CASCADE",
      sqlu"TRUNCATE TABLE ilmoittautumiset CASCADE",
      sqlu"TRUNCATE TABLE ilmoittautumiset_history CASCADE",
      sqlu"TRUNCATE TABLE pistetiedot CASCADE",
      sqlu"TRUNCATE TABLE valinnantulokset CASCADE",
      sqlu"TRUNCATE TABLE valinnantulokset_history CASCADE",
      sqlu"TRUNCATE TABLE valinnantilat CASCADE",
      sqlu"TRUNCATE TABLE valinnantilat_history CASCADE",
      sqlu"TRUNCATE TABLE jonosijat CASCADE",
      sqlu"TRUNCATE TABLE valintatapajonot CASCADE",
      sqlu"TRUNCATE TABLE sijoitteluajon_hakukohteet CASCADE",
      sqlu"TRUNCATE TABLE hakukohteet CASCADE",
      sqlu"TRUNCATE TABLE sijoitteluajot CASCADE",
      sqlu"TRUNCATE TABLE lukuvuosimaksut CASCADE"
    ).transactionally)
  }

  private def getVastaanottoAction(vastaanotto: String) = vastaanotto match {
    case "KESKEN" => None
    case "EI_VASTAANOTETTU_MAARA_AIKANA" => Some(MerkitseMyohastyneeksi)
    case "PERUNUT" => Some(Peru)
    case "PERUUTETTU" => Some(Peruuta)
    case "EHDOLLISESTI_VASTAANOTTANUT" => Some(VastaanotaEhdollisesti)
    case "VASTAANOTTANUT_SITOVASTI" => Some(VastaanotaSitovasti)
  }

  def clearFixtures() {
    deleteAll()
    //    MongoMockData.clear(db)
    //    val base = MongoMockData.readJson("fixtures/sijoittelu/sijoittelu-basedata.json")
    //    MongoMockData.insertData(db, base)
  }
}
