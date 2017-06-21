package fi.vm.sade.valintatulosservice.sijoittelu.fixture

import fi.vm.sade.sijoittelu.domain.Valintatulos
import fi.vm.sade.valintatulosservice.json4sCustomFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.{DefaultFormats, JValue}
import org.json4s.JsonAST.JArray
import org.json4s.jackson.JsonMethods._
import org.springframework.core.io.ClassPathResource
import slick.driver.PostgresDriver.api.{actionBasedSQLInterpolation, _}

import scala.collection.immutable

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
    val text = scala.io.Source.fromInputStream(new ClassPathResource("fixtures/sijoittelu/" + fixtureName).getInputStream).mkString
    val json = parse(text)
    SijoitteluWrapper.fromJson(json) match {
      case Some(wrapper) =>
        wrapper.hakukohteet.foreach(h => storeHakukohde(h.getOid, wrapper.sijoitteluajo.getHakuOid, wrapper.sijoitteluajo.getSijoitteluajoId, h.isKaikkiJonotSijoiteltu, yhdenPaikanSaantoVoimassa, kktutkintoonJohtava))
        val valintatulokset: Seq[Valintatulos] = wrapper.valintatulokset
        valintarekisteriDb.storeSijoittelu(wrapper.copy(valintatulokset = List()))
        storeValintatulokset(valintatulokset, wrapper.sijoitteluajo.getSijoitteluajoId)
        storeEhdollisenHyvaksynnanEhto(json)
        storeVastaanotot(json)
      case None =>
    }
  }

  private def storeValintatulokset(valintatulokset: Seq[Valintatulos], sijoitteluAjoId: Long) = {
    valintatulokset.foreach(tulos => {
      valintarekisteriDb.runBlocking(valintarekisteriDb.storeValinnantuloksenOhjaus(
        ValinnantuloksenOhjaus(
          HakemusOid(tulos.getHakemusOid),
          ValintatapajonoOid(tulos.getValintatapajonoOid),
          HakukohdeOid(tulos.getHakukohdeOid),
          tulos.getEhdollisestiHyvaksyttavissa,
          tulos.getJulkaistavissa,
          tulos.getHyvaksyttyVarasijalta,
          tulos.getHyvaksyPeruuntunut,
          sijoitteluAjoId.toString,
          "Sijoitteluajon tallennus")
      ))
    })
  }

  private def storeEhdollisenHyvaksynnanEhto(json: JValue) = {
    val JArray(valintatulokset) = (json \ "Valintatulos")

    for (valintatulos <- valintatulokset) {
      val ehdollisenHyvaksynnanEhtoOption = (valintatulos \ "ehdollisestiHyvaksyttavissa").extractOpt[Boolean]
      ehdollisenHyvaksynnanEhtoOption match {
        case None =>
        case Some(ehdollisenHyvaksynnanEhto) =>
          if (ehdollisenHyvaksynnanEhto) {
            val ehto = EhdollisenHyvaksynnanEhto(
              (valintatulos \ "hakemusOid").extract[HakemusOid],
              (valintatulos \ "valintatapajonoOid").extract[ValintatapajonoOid],
              (valintatulos \ "hakukohdeOid").extract[HakukohdeOid],
              (valintatulos \ "ehdollisenHyvaksymisenEhtoKoodi").extractOpt[String].orNull,
              (valintatulos \ "ehdollisenHyvaksymisenEhtoFI").extractOpt[String].orNull,
              (valintatulos \ "ehdollisenHyvaksymisenEhtoSV").extractOpt[String].orNull,
              (valintatulos \ "ehdollisenHyvaksymisenEhtoEN").extractOpt[String].orNull
            )
            valintarekisteriDb.runBlocking(valintarekisteriDb.storeEhdollisenHyvaksynnanEhto(ehto))
          }
      }
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
    sqlu"truncate table vastaanotot cascade",
    sqlu"delete from deleted_vastaanotot where id <> overriden_vastaanotto_deleted_id()",
    sqlu"truncate table henkiloviitteet cascade",
    sqlu"truncate table vanhat_vastaanotot cascade")

  def deleteAll(): Unit = {
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
      sqlu"truncate table valintaesitykset cascade",
      sqlu"truncate table valintaesitykset_history cascade ",
      sqlu"truncate table jonosijat cascade",
      sqlu"truncate table valintatapajonot cascade",
      sqlu"truncate table sijoitteluajon_hakukohteet cascade",
      sqlu"truncate table hakukohteet cascade",
      sqlu"truncate table sijoitteluajot cascade",
      sqlu"truncate table lukuvuosimaksut cascade"
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
