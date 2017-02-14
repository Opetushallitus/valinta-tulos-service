package fi.vm.sade.valintatulosservice.migraatio.vastaanotot

import java.util.Date

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.TypeImports.{BasicDBList => _, DBObject => _}
import fi.vm.sade.sijoittelu.domain.{ValintatuloksenTila, Valintatulos}
import fi.vm.sade.sijoittelu.tulos.service.RaportointiService
import fi.vm.sade.valintatulosservice.VtsServletBase
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.tarjonta.HakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.apache.commons.lang3.StringUtils
import org.mongodb.morphia.Datastore
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.springframework.util.StopWatch

import scala.collection.JavaConverters._

class MigraatioServlet(hakukohdeRecordService: HakukohdeRecordService, valintarekisteriDb: ValintarekisteriDb,
                       hakemusRepository: HakemusRepository, raportointiService: RaportointiService, hakuService: HakuService)
                      (implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("migraatio")

  override protected def applicationDescription: String = "Vanhojen vastaanottojen migraatio REST API"

  logger.warn("Mountataan Valintarekisterin migraatioservlet!")

  private val migraatioSijoittelutulosService = new MerkitsevaValintatapaJonoResolver(raportointiService)
  private val missingHakijaOidResolver = new MissingHakijaOidResolver(appConfig)
  private val mongoConfig = appConfig.settings.valintatulosMongoConfig
  private val morphia: Datastore = appConfig.sijoitteluContext.morphiaDs
  private val valintatulosMongoCollection = MongoFactory.createDB(mongoConfig)("Valintatulos")

  private type HakuOid = String
  private type HakijaOid = String
  private type HakukohdeOid = String
  private val hakuOidToExistingVastaanotot: scala.collection.mutable.Map[HakuOid, scala.collection.mutable.Set[(HakijaOid, HakukohdeOid)]] = scala.collection.mutable.Map()

  val getMigraatioHakukohteetSwagger: OperationBuilder = (apiOperation[List[String]]("tuoHakukohteet")
    summary "Migraatio hakukohteille")
  get("/hakukohteet", operation(getMigraatioHakukohteetSwagger)) {
    val stopWatch = new StopWatch("Hakukohteiden migraatio")

    logger.info("Aloitetaan hakukohteiden migraatio, haetaan uniikit hakuoidit valintatuloksista:")
    stopWatch.start("uniikkien hakuOidien haku")
    val hakuOids = haeHakuOidit
    stopWatch.stop()
    logger.info(s"Löytyi ${hakuOids.size} hakuOidia")

    logger.info("Haetaan uniikit hakukohdeoidit tarjonnasta:")
    stopWatch.start("uniikkien hakukohdeOidien haku")
    val hakukohdeOidsFromTarjonta = hakuOids.flatMap(hakuService.getHakukohdeOids(_) match {
      case Right(oids) => oids
      case Left(e) => throw e
    })
    stopWatch.stop()
    logger.info(s"Löytyi ${hakukohdeOidsFromTarjonta.size} hakukohdeOidia tarjonnasta")

    logger.info("Haetaan uniikit hakukohdeoidit Mongosta:")
    stopWatch.start("uniikkien hakukohdeOidien haku")
    val hakukohdeOidsFromMongo = haeHakukohdeOidit
    stopWatch.stop()
    logger.info(s"Löytyi ${hakukohdeOidsFromMongo.size} hakukohdeOidia Mongosta")

    val hakukohdeOids = ( hakukohdeOidsFromTarjonta.toList ++ hakukohdeOidsFromMongo.toList ).toSet


    logger.info(s"Löytyi yhteensä ${hakukohdeOids.size} uniikkia hakukohdeoidia")
    logger.info(s"Mongosta löytyi hakukohteet: ${hakukohdeOidsFromMongo.toSet -- hakukohdeOidsFromTarjonta.toSet}, joita ei löytynyt tarjonnasta.")

    logger.info("Hakukohteiden tarkistaminen kannasta ja hakeminen tarjonnasta tarvittaessa:")
    stopWatch.start("hakukohteiden tarkistaminen ja tallentaminen")
    hakukohdeOids.foreach(oid => {
      hakukohdeRecordService.getHakukohdeRecord(oid)
        .left.foreach(e => logger.warn(s"Ongelma haettaessa hakukohdetta $oid: ${e.getMessage}"))
    })
    stopWatch.stop()

    logger.info(s"${hakukohdeOids.size} hakukohdeOidia käsitelty, hakukohteiden migraatio on valmis.")
    logger.info(s"Hakukohdemigraation vaiheiden kestot:\n${stopWatch.prettyPrint()}")
    Ok(hakukohdeOids)
  }

  private def haeHakuOidit: Iterable[HakuOid] = {
    morphia.getCollection(classOf[Valintatulos]).distinct("hakuOid").asScala collect { case s:String if StringUtils.isNotBlank(s) => s }
  }

  private def haeHakukohdeOidit: Iterable[HakukohdeOid] = {
    morphia.getCollection(classOf[Valintatulos]).distinct("hakukohdeOid").asScala collect { case s:String if StringUtils.isNotBlank(s) => s }
  }

  val getMigraatioVastaanototSwagger: OperationBuilder = (apiOperation[List[String]]("tuoVastaanotot")
    summary "Migraatio valintatulosten vastaanotoille")
  get("/vastaanotot", operation(getMigraatioVastaanototSwagger)) {
    //1) hae valintatulos-objektit, joissa tila != kesken
    //2) jos samalle hakijalle ja hakukohteelle on monta valintatulosta
    //   -> hae sijoittelun tulos hakukohteelle, jos sitä ei ole jo haettu
    //   -> valitse merkitsevä jono valinnan tilan perusteella
    //3) jos hakijaoid puuttuu valintatuloksesta -> hae hakemus haku-appista
    //   -> jos hakijaoid puuttuu myös hakemukselta, skippaa valintatulos
    //4) tallenna vastaanottoAction

    val stopWatch = new StopWatch("Valintatulosten migraatio")

    stopWatch.start("haetaan valintatulokset sijoittelu-mongosta ja ryhmitellään hakemuksittain ja hakukohteittain")
    logger.info("Aloitetaan valintatulosten migraatio: haetaan valintatulokset sijoittelusta")
    val valintatuloksetByHakemusJaHakukohde = findValintatulokset.groupBy(vt => (vt.hakemusOid, vt.hakukohdeOid))
    stopWatch.stop()
    logger.info(s"Löytyi ${valintatuloksetByHakemusJaHakukohde.size} hakemuksen valintatulosta")
    val (yksiValintatulosPerHakukohde, montaValintatulostaPerHakukohde) = valintatuloksetByHakemusJaHakukohde.partition(_._2.size == 1)

    stopWatch.start("haetaan merkitsevat valintatulokset useamman jonon tapauksista")
    logger.info(s"haetaan merkitsevat valintatulokset useamman jonon tapauksista, joita on ${montaValintatulostaPerHakukohde.size}")
    val merkitsevatValintatuloksetUseammanJononTapauksista = montaValintatulostaPerHakukohde.map(poimiMerkitseva)
    stopWatch.stop()

    val tallennettavatValintatulokset = yksiValintatulosPerHakukohde ++ merkitsevatValintatuloksetUseammanJononTapauksista

    stopWatch.start("tallennetaan migroidut valintatulokset")
    logger.info(s"Tallennetaan ${tallennettavatValintatulokset.size} valintatulosta")
    val tallennetetutHakemusOidit = tallennettavatValintatulokset.flatMap { case ((hakemusOid, hakukohdeOid), valintatulokset) =>
      tallenna(valintatulokset.head)
    }.map(_.hakemusOid)
    stopWatch.stop()
    logger.info("Valintatulosten migraatio on valmis. Vaiheiden kestot:")
    logger.info(stopWatch.prettyPrint())
    Ok(tallennetetutHakemusOidit)
  }

  def poimiMerkitseva(tulokset: ((String, String), List[MigraatioValintatulos])): ((String, String), List[MigraatioValintatulos]) = {
    val ((hakemusOid, hakukohdeOid), valintatulokset) = tulokset
    ((hakemusOid, hakukohdeOid), List(findMerkitsevaValintatulos(hakemusOid, hakukohdeOid, valintatulokset)))
  }

  def findMerkitsevaValintatulos(hakemusOid: String, hakukohdeOid: HakukohdeOid,
                                 kaikkiHakemuksenEiKeskenValintatulokset: List[MigraatioValintatulos]): MigraatioValintatulos = {
    def haveAllSameTila(ts: Seq[MigraatioValintatulos]): Boolean = ts.map(_.tila).toSet.size == 1

    val tuloksetHakijaOidienKanssa: List[MigraatioValintatulos] = kaikkiHakemuksenEiKeskenValintatulokset.map(resolveHakijaOidIfMissing)
    val hakuOid = tuloksetHakijaOidienKanssa.head.hakuOid
    val hakijaOid = tuloksetHakijaOidienKanssa.head.hakijaOid
    migraatioSijoittelutulosService.hakemuksenKohteidenMerkitsevatJonot(hakuOid, hakemusOid, hakijaOid).flatMap(_.find(_._1 == hakukohdeOid)).map(_._2) match {
      case Some(jonoOid) => tuloksetHakijaOidienKanssa.find(_.valintatapajonoOid == jonoOid).getOrElse {
        val query = Map("$and" -> List(
          Map("hakemusOid" -> hakemusOid),
          Map("valintatapajonoOid" -> jonoOid)
        ))
        findValintatulokset(query).head
      }
      case None => val ensimmainenTulos = tuloksetHakijaOidienKanssa.head
        if (haveAllSameTila(tuloksetHakijaOidienKanssa)) {
        logger.warn(s"Ei löydy merkitsevää valintatapajonoOidia hakemuksen $hakemusOid kohteelle $hakukohdeOid " +
          s", mutta kaikkien valintatulosten tila on sama ${ensimmainenTulos.tila}, joten palautetaan ensimmäinen tulos $ensimmainenTulos")
      } else {
        logger.warn(s"Ei löydy merkitsevää valintatapajonoOidia hakemuksen $hakemusOid kohteelle $hakukohdeOid " +
          s", ja valintatuloksilla on eri tiloja ${tuloksetHakijaOidienKanssa.map(_.tila)}, joten ei voida tehdä muuta kuin palautetaan ensimmäinen tulos $ensimmainenTulos näistä $tuloksetHakijaOidienKanssa")
      }
      ensimmainenTulos
    }
  }

  private def tallenna(valintatulosFromMongo: MigraatioValintatulos): Option[VirkailijanVastaanotto] = {
    if (valintatulosFromMongo.tila == "KESKEN" || valintatulosFromMongo.tila == "ILMOITETTU") {
      logger.warn(s"Paras arvaus vastaanottotilaksi on tila ${valintatulosFromMongo.tila}, jota ei tallenneta kantaan: $valintatulosFromMongo")
      None
    } else {
      try {
        val valintatulos = resolveHakijaOidIfMissing(valintatulosFromMongo)

        if (!vastaanottoForHakijaAndHakukohdeExists(valintatulos.hakuOid, valintatulos.hakijaOid, valintatulos.hakukohdeOid)) {
          val (vastaanotto, luotu) = createVirkailijanVastaanotto(valintatulos)
          try {
            valintarekisteriDb.store(vastaanotto, luotu)
          } catch {
            case e: Exception => logger.error(s"Virhe tallennettaessa vastaanottoa $vastaanotto", e)
          }
          addToExistingVastaanottosCache(valintatulos, vastaanotto)
          Some(vastaanotto)
        } else {
          None
        }

      } catch {
        case sve:SkipValintatulosException => logger.warn(sve.getMessage)
          None
      }
    }
  }

  private def createVirkailijanVastaanotto(valintatulos: MigraatioValintatulos): (VirkailijanVastaanotto, Date) = {
    val (muokkaaja, selite, luotu) = resolveIlmoittajaJaSeliteJaLuontipvm(valintatulos)

    ( VirkailijanVastaanotto(
      valintatulos.hakuOid,
      valintatulos.valintatapajonoOid,
      resolveHakijaOidIfMissing(valintatulos).hakijaOid,
      valintatulos.hakemusOid,
      valintatulos.hakukohdeOid,
      convertLegacyTilaToAction(valintatulos),
      muokkaaja,
      selite), luotu)
  }

  private def resolveIlmoittajaJaSeliteJaLuontipvm(valintatulos: MigraatioValintatulos): (String, String, Date) = {
    val descLogEntries = valintatulos.logEntries.sortWith((a, b) => a.luotu.after(b.luotu))

    descLogEntries.find(e => e.muutos.startsWith("tila:") && e.muutos.contains(valintatulos.tila))
      .orElse(descLogEntries.find(e => StringUtils.contains(e.muutos, valintatulos.tila))) match {
        case None => ("järjestelmä", "migraatio", new Date())
        case Some(e) => (e.muokkaaja, e.selite, e.luotu)
    }
  }

  private def findValintatulokset: List[MigraatioValintatulos] = {
    val query = Map("$and" -> List(
      Map("hakemusOid" -> Map("$exists" -> true)),
      Map("tila" -> Map("$exists" -> true)),
      Map("tila" -> Map("$ne" -> ValintatuloksenTila.KESKEN.toString)),
      Map("tila" -> Map("$ne" -> "ILMOITETTU"))
    ))
    findValintatulokset(query)
  }

  private def findValintatulokset(query: DBObject): List[MigraatioValintatulos] = {
    valintatulosMongoCollection.find(query).toList.map(o => {
      MigraatioValintatulos(
        o.get("hakuOid").asInstanceOf[String],
        o.get("hakijaOid").asInstanceOf[String],
        o.get("hakemusOid").asInstanceOf[String],
        o.get("hakukohdeOid").asInstanceOf[String],
        o.get("tila").asInstanceOf[String],
        o.get("valintatapajonoOid").asInstanceOf[String],
        parseLogentries(o)
      )
    })
  }

  private def parseLogentries(valintatulosMongoObject: DBObject): List[MigraatioLogEntry] = valintatulosMongoObject.get("logEntries") match {
    case null => Nil
    case list => list.asInstanceOf[BasicDBList].toList.map(e => MigraatioLogEntry(
      e.asInstanceOf[DBObject].get("muutos").asInstanceOf[String],
      formatMuokkaaja(e.asInstanceOf[DBObject].get("muokkaaja").asInstanceOf[String]),
      e.asInstanceOf[DBObject].get("selite").asInstanceOf[String],
      e.asInstanceOf[DBObject].get("luotu").asInstanceOf[Date])
    )
  }

  def formatMuokkaaja(muokkaaja:String): String = muokkaaja match {
    case x if x.startsWith("henkilö:") => x.substring(8)
    case x => x
  }

  case class MigraatioValintatulos(hakuOid: String, hakijaOid: String, hakemusOid: String, hakukohdeOid: String, tila: String,
                                   valintatapajonoOid: String, logEntries: List[MigraatioLogEntry])
  case class MigraatioLogEntry(muutos:String, muokkaaja:String, selite:String, luotu:Date)

  def convertLegacyTilaToAction(valintatulos: MigraatioValintatulos): VirkailijanVastaanottoAction = valintatulos.tila match {
    case "VASTAANOTTANUT_SITOVASTI" => VastaanotaSitovasti
    case "VASTAANOTTANUT" => VastaanotaSitovasti
    case "VASTAANOTTANUT_LASNA" =>  VastaanotaSitovasti
    case "VASTAANOTTANUT_POISSAOLEVA" => VastaanotaSitovasti
    case "EHDOLLISESTI_VASTAANOTTANUT" => VastaanotaEhdollisesti
    case "EI_VASTAANOTETTU_MAARA_AIKANA" => MerkitseMyohastyneeksi
    case "PERUNUT" => Peru
    case "PERUUTETTU" => Peruuta
    case x => throw new UnsupportedOperationException(s"Tuntematon tila valintatulos-objektissa: $x , valintatulos: $valintatulos")
  }

  private def vastaanottoForHakijaAndHakukohdeExists(hakuOid: HakuOid, hakijaOid: HakijaOid, hakukohdeOid: HakukohdeOid): Boolean = {
    if (hakijaOid == null || hakijaOid == "") {
      throw new IllegalArgumentException("hakijaOid puuttuu")
    }
    if (!hakuOidToExistingVastaanotot.contains(hakuOid)) {
      populateExistingVastaanottosCacheFromDbForHaku(hakuOid)
    }
    hakuOidToExistingVastaanotot(hakuOid).contains((hakijaOid, hakukohdeOid))
  }

  private def populateExistingVastaanottosCacheFromDbForHaku(hakuOid: HakuOid): hakuOidToExistingVastaanotot.type = {
    val existingVastaanottosFromDb = scala.collection.mutable.Set[(HakijaOid, HakukohdeOid)]() ++=
      valintarekisteriDb.findHaunVastaanotot(hakuOid).map(v => (v.henkiloOid, v.hakukohdeOid))
    hakuOidToExistingVastaanotot += hakuOid -> existingVastaanottosFromDb
  }

  private def addToExistingVastaanottosCache(valintatulos: MigraatioValintatulos, vastaanotto: VirkailijanVastaanotto): Boolean = {
    hakuOidToExistingVastaanotot(valintatulos.hakuOid).add((vastaanotto.henkiloOid, vastaanotto.hakukohdeOid))
  }

  private def resolveHakijaOidIfMissing(valintatulos: MigraatioValintatulos): MigraatioValintatulos = valintatulos.hakijaOid match {
    case hakijaOid if StringUtils.isBlank(hakijaOid) => hakemusRepository.findHakemus(valintatulos.hakemusOid) match {
      case Left(e) =>
        resolvePersonOidFromHakemusAndHenkiloPalvelu(hakijaOid, valintatulos, s"Hakemusta ${valintatulos.hakemusOid} ei löydy valintatulokselle $valintatulos!")
      case Right(hakemus) if StringUtils.isBlank(hakemus.henkiloOid) => resolvePersonOidFromHakemusAndHenkiloPalvelu(hakijaOid, valintatulos, s"Valintatulokselle ei löydy hakijaOidia hakemukselta ${valintatulos.hakemusOid}")
      case Right(hakemus) => valintatulos.copy(hakijaOid = hakemus.henkiloOid)
    }
    case _ => valintatulos
  }

  private def resolvePersonOidFromHakemusAndHenkiloPalvelu(hakijaOid: String, valintatulos: MigraatioValintatulos, message: String): MigraatioValintatulos = {
    logger.warn(message)
    missingHakijaOidResolver.findPersonOidByHakemusOid(valintatulos.hakemusOid).map { personOid: String => valintatulos.copy(hakijaOid = personOid) }.getOrElse(
      throw new SkipValintatulosException("Ei löytynyt henkilöOidia henkilötunnuksen perusteella: " + message))
  }

  class SkipValintatulosException(message:String) extends Exception(message)

}

