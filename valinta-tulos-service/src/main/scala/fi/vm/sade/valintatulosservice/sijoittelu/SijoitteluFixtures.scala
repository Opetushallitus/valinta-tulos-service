package fi.vm.sade.valintatulosservice.sijoittelu

import java.util.concurrent.TimeUnit.SECONDS

import com.mongodb.DB
import fi.vm.sade.sijoittelu.tulos.testfixtures.MongoMockData
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JArray
import org.json4s.jackson.JsonMethods._
import org.springframework.core.io.ClassPathResource
import slick.driver.PostgresDriver.api.actionBasedSQLInterpolation

import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class SijoitteluFixtures(db: DB, valintarekisteriDb : ValintarekisteriDb) {
  def importFixture(fixtureName: String,
                    clear: Boolean = false,
                    yhdenPaikanSaantoVoimassa: Boolean = false,
                    kktutkintoonJohtava: Boolean = false) {
    if (clear) {
      clearFixtures
      Await.result(valintarekisteriDb.db.run(sqlu"DELETE FROM vastaanotot"), Duration(4, SECONDS))
      Await.result(valintarekisteriDb.db.run(sqlu"DELETE FROM ilmoittautumiset"), Duration(4, SECONDS))
      Await.result(valintarekisteriDb.db.run(sqlu"DELETE FROM hakukohteet"), Duration(4, SECONDS))
    }
    val tulokset = MongoMockData.readJson("fixtures/sijoittelu/" + fixtureName)
    MongoMockData.insertData(db, tulokset)

    importJsonFixturesToPostgres(fixtureName, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava);

  }

  private def importJsonFixturesToPostgres(fixtureName: String,
                                           yhdenPaikanSaantoVoimassa: Boolean = false,
                                           kktutkintoonJohtava: Boolean = false): Unit = {

    implicit val formats = DefaultFormats

    val json = parse(scala.io.Source.fromInputStream(new ClassPathResource("fixtures/sijoittelu/" + fixtureName).getInputStream).mkString)
    val JArray(valintatulokset) = ( json \ "Valintatulos" )

    for(valintatulos <- valintatulokset) {
      val tilaOption = (valintatulos \ "tila").extractOpt[String]
      tilaOption match {
        case None =>
          // pass
        case Some(tila) =>
          getVastaanottoAction(tila).foreach(action => {
            valintarekisteriDb.storeHakukohde(HakukohdeRecord(
                (valintatulos \ "hakukohdeOid").extract[String],
                (valintatulos \ "hakuOid").extract[String],
                yhdenPaikanSaantoVoimassa,
                kktutkintoonJohtava,
                Kevat(2016)
            ))

            valintarekisteriDb.store(VirkailijanVastaanotto(
              (valintatulos \ "hakuOid").extract[String],
              (valintatulos \ "valintatapajonoOid").extract[String],
              (valintatulos \ "hakijaOid").extract[String],
              (valintatulos \ "hakemusOid").extract[String],
              (valintatulos \ "hakukohdeOid").extract[String],
              action,
              (valintatulos \ "hakijaOid").extract[String],
              "Tuotu vanhasta järjestelmästä"
            ))
          })
      }

    }
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
