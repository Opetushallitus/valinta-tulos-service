package fi.vm.sade.valintatulosservice.sijoittelu.fixture

import com.mongodb.DB
import fi.vm.sade.sijoittelu.tulos.testfixtures.MongoMockData
import fi.vm.sade.valintatulosservice.json4sCustomFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._
import org.springframework.core.io.ClassPathResource
import slick.driver.PostgresDriver.api.{actionBasedSQLInterpolation, _}

case class SijoitteluFixtures(db: DB, valintarekisteriDb : ValintarekisteriDb) extends json4sCustomFormats {

  implicit val formats = DefaultFormats ++ List(
    new NumberLongSerializer)

  def importFixture(fixtureName: String,
                    clear: Boolean = false,
                    yhdenPaikanSaantoVoimassa: Boolean = false,
                    kktutkintoonJohtava: Boolean = false) {
    if (clear) {
      clearFixtures
      deleteAll()
    }
    val tulokset = MongoMockData.readJson("fixtures/sijoittelu/" + fixtureName)
    MongoMockData.insertData(db, tulokset)

    importJsonFixturesToPostgres(fixtureName, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava)

  }

  private def importJsonFixturesToPostgres(fixtureName: String,
                                           yhdenPaikanSaantoVoimassa: Boolean = false,
                                           kktutkintoonJohtava: Boolean = false): Unit = {

    val json = parse(scala.io.Source.fromInputStream(new ClassPathResource("fixtures/sijoittelu/" + fixtureName).getInputStream).mkString)
    val wrapper = SijoitteluWrapper.fromJson(json)
    wrapper.hakukohteet.foreach(h => insertHakukohde(h.getOid, wrapper.sijoitteluajo.getHakuOid, wrapper.sijoitteluajo.getSijoitteluajoId, h.isKaikkiJonotSijoiteltu, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava))
    valintarekisteriDb.storeSijoittelu(wrapper)
  }

  private def insertHakukohde(hakukohdeOid: String, hakuOid: String, sijoitteluajoId: Long, kaikkiJonotSijoiteltu: Boolean, yhdenPaikanSaantoVoimassa: Boolean, kktutkintoonJohtava: Boolean) = {
    valintarekisteriDb.storeHakukohde(HakukohdeRecord(HakukohdeOid(hakukohdeOid), HakuOid(hakuOid), yhdenPaikanSaantoVoimassa, kktutkintoonJohtava, Kevat(2016)))
    valintarekisteriDb.runBlocking(
      sqlu"""INSERT INTO sijoitteluajon_hakukohteet (sijoitteluajo_id, haku_oid, hakukohde_oid, kaikki_jonot_sijoiteltu)
               VALUES (${sijoitteluajoId}, ${hakuOid}, ${hakukohdeOid}, ${kaikkiJonotSijoiteltu})
               ON CONFLICT DO NOTHING"""
    )
  }

  private val deleteFromVastaanotot = DBIO.seq(
    sqlu"truncate table vastaanotot cascade",
    sqlu"truncate table deleted_vastaanotot cascade",
    sqlu"truncate table henkiloviitteet cascade",
    sqlu"truncate table vanhat_vastaanotot cascade")

  private def deleteAll(): Unit = {
    valintarekisteriDb.runBlocking(DBIO.seq(
      deleteFromVastaanotot,
      sqlu"truncate table valinnantilan_kuvaukset cascade",
      sqlu"truncate table hakijaryhman_hakemukset cascade",
      sqlu"truncate table hakijaryhmat cascade",
      sqlu"truncate table ilmoittautumiset cascade",
      sqlu"truncate table ilmoittautumiset_history cascade",
      sqlu"truncate table pistetiedot cascade",
      sqlu"truncate table valinnantulokset cascade",
      sqlu"truncate table valinnantulokset_history cascade",
      sqlu"truncate table valinnantilat cascade",
      sqlu"truncate table valinnantilat_history cascade",
      sqlu"truncate table jonosijat cascade",
      sqlu"truncate table valintatapajonot cascade",
      sqlu"truncate table sijoitteluajon_hakukohteet cascade",
      sqlu"truncate table hakukohteet cascade",
      sqlu"truncate table sijoitteluajot cascade",
      sqlu"truncate table lukuvuosimaksut cascade"
    ).transactionally)
  }

  private def getVastaanottoAction(vastaanotto:String) = vastaanotto match {
    case "KESKEN" => None
    case "EI_VASTAANOTETTU_MAARA_AIKANA" => Some(MerkitseMyohastyneeksi)
    case "PERUNUT" => Some(Peru)
    case "PERUUTETTU" => Some(Peruuta)
    case "EHDOLLISESTI_VASTAANOTTANUT" => Some(VastaanotaEhdollisesti)
    case "VASTAANOTTANUT_SITOVASTI" => Some(VastaanotaSitovasti)
  }

  def clearFixtures {
    MongoMockData.clear(db)
    val base = MongoMockData.readJson("fixtures/sijoittelu/sijoittelu-basedata.json")
    MongoMockData.insertData(db, base)
  }
}
