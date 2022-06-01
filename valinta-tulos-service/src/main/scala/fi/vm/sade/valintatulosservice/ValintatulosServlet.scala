package fi.vm.sade.valintatulosservice

import java.util.Date
import fi.vm.sade.auditlog.Operation
import fi.vm.sade.sijoittelu.tulos.dto.IlmoittautumisTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats.javaObjectToJsonString
import fi.vm.sade.valintatulosservice.json.{JsonFormats, JsonStreamWriter, StreamingFailureException}
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.streamingresults.{HakemustenTulosHakuLock, StreamingValintatulosService}
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import javax.servlet.http.HttpServletResponse
import org.joda.time.DateTime
import org.json4s.Extraction
import org.json4s.jackson.Serialization.read
import org.scalatra._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.Try



abstract class ValintatulosServlet(valintatulosService: ValintatulosService,
                                   streamingValintatulosService: StreamingValintatulosService,
                                   vastaanottoService: VastaanottoService,
                                   ilmoittautumisService: IlmoittautumisService,
                                   valintarekisteriDb: ValintarekisteriDb,
                                   hakemustenTulosHakuLock: HakemustenTulosHakuLock,
                                   swaggerGroupTag: String)
                                  (implicit val swagger: Swagger,
                                   appConfig: VtsAppConfig) extends VtsServletBase {
  val ilmoittautumisenAikaleima: Option[Date] = Option(new Date())
  lazy val exampleHakemuksenTulos = Hakemuksentulos(
    HakuOid("2.2.2.2"),
    HakemusOid("4.3.2.1"),
    "1.3.3.1",
    Vastaanottoaikataulu(Some(new DateTime()), Some(14)),
    List(
      Hakutoiveentulos.julkaistavaVersioSijoittelunTuloksesta(ilmoittautumisenAikaleima,
        HakutoiveenSijoitteluntulos.kesken(HakukohdeOid("1.2.3.4"), "4.4.4.4"),
        Hakutoive(HakukohdeOid("1.2.3.4"), "4.4.4.4", "Hakukohde1", "Tarjoaja1"),
        Haku(
          HakuOid("5.5.5.5"),
          korkeakoulu = true,
          toinenAste = false,
          sallittuKohdejoukkoKelaLinkille = true,
          käyttääSijoittelua = true,
          käyttääHakutoiveidenPriorisointia = true,
          varsinaisenHaunOid = None,
          sisältyvätHaut = Set(),
          koulutuksenAlkamiskausi = Some(Kausi("2016S")),
          yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, ""),
          nimi = Map("kieli_fi" -> "Haun nimi")),
        Ohjausparametrit(Vastaanottoaikataulu(None, None), Some(DateTime.now().plusDays(10)), Some(DateTime.now().plusDays(30)), Some(DateTime.now().plusDays(60)), None, None, None, true, true, true),
        hasHetu = true
      )
    )
  )

  // Real return type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
  lazy val getHakemusSwagger: OperationBuilder = (apiOperation[Unit]("getHakemus")
    summary "Hae hakemuksen tulokset."
    notes "Palauttaa tyyppiä Hakemuksentulos. Esim:\n" +
      pretty(Extraction.decompose(exampleHakemuksenTulos))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid, jonka tulokset halutaan")
    tags swaggerGroupTag)
  get("/:hakuOid/hakemus/:hakemusOid", operation(getHakemusSwagger)) {
    val hakemusOidString = params("hakemusOid")
    auditLog(Map("hakuOid" -> params("hakuOid"), "hakemusOid" -> hakemusOidString), HakemuksenLuku)
    valintatulosService.hakemuksentulos(HakemusOid(hakemusOidString)) match {
      case Some(tulos) => tulos
      case _ => NotFound("error" -> "Not found")
    }
  }

  lazy val getValintatuloksetByHakemuksetSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHakemukset")
    summary "Hakee hakemuksen valintatulokset hakemuksille"
    parameter bodyParam[Set[String]]("hakemusOids").description("Kiinnostavien hakemusten oidit")
    tags swaggerGroupTag)
  post("/hakemukset", operation(getValintatuloksetByHakemuksetSwagger)) {
    val hakemusOids = read[Set[HakemusOid]](request.body)
    Ok(valintatulosService.hakemuksentulos(hakemusOids))
  }

  lazy val getValintatuloksetByHakemuksetForValpasSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHakemuksetForValpas")
    summary "Hakee hakemuksien valintatulokset Valpas-palvelua varten"
    parameter bodyParam[ValpasValinnantuloksetKysely]("hakemusOids").description("Kiinnostavien hakemusten henkilo-oidit ja vastaavat hakemusoidit")
    parameter pathParam[String]("hakuOid").description("Haun oid")
    tags swaggerGroupTag)
  post("/hakemukset/valpas/:hakuOid", operation(getValintatuloksetByHakemuksetForValpasSwagger)) {
    val henkiloOidToHakemukset = read[ValpasValinnantuloksetKysely](request.body)
    val hakuOid: HakuOid = HakuOid(params("hakuOid"))
    logger.info(s"Haetaan hakemuksen tiedot haulle ${hakuOid} valintarekisteri Valpas-palvelua varten")
    Ok(valintatulosService.valpasHakemuksienTulokset(hakuOid, henkiloOidToHakemukset))
  }

  lazy val getHakemuksetSwagger: OperationBuilder = (apiOperation[Unit]("getHakemukset")
    summary "Hae haun kaikkien hakemusten tulokset. Palauttaa julkaistu tilaiset valintatulokset jo ennen haun tulosten julkaisupäivää."
    notes "Palauttaa tyyppiä Seq[Hakemuksentulos]. Esim:\n" +
      pretty(Extraction.decompose(Seq(exampleHakemuksenTulos)))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    tags swaggerGroupTag)
  get("/:hakuOid", operation(getHakemuksetSwagger)) {
    val hakuOidString = params("hakuOid")
    val info = hakuOidString + "_" + System.currentTimeMillis()
    logger.info(s"getHakemuksetForHaku: $hakuOidString")
    auditLog(Map("hakuOid" -> hakuOidString), HakemuksenLuku)
    serveStreamingResults({ valintatulosService.hakemustenTulosByHaku(HakuOid(hakuOidString), false) }, info)
  }

  get("/:hakuOid/hakukohde/:hakukohdeOid", operation(getHakukohteenHakemuksetSwagger)) {
    val hakuOidString = params("hakuOid")
    val hakukohdeOidString = params("hakukohdeOid")
    auditLog(Map("hakuOid" -> hakuOidString, "hakukohdeOid" -> hakukohdeOidString), HakemuksenLuku)
    serveStreamingResults({ valintatulosService.hakemustenTulosByHakukohde(HakuOid(hakuOidString), HakukohdeOid(hakukohdeOidString)).right.toOption })
  }

  lazy val getHakukohteenHakemuksetSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenHakemukset")
    summary "Hae hakukohteen kaikkien hakemusten tulokset."
    notes "Palauttaa tyyppiä Seq[Hakemuksentulos]. Esim:\n" +
    pretty(Extraction.decompose(Seq(exampleHakemuksenTulos)))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    tags swaggerGroupTag)

  lazy val getHakukohteenVastaanotettavuusSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenHakemukset")
    summary "Palauttaa 200 jos hakutoive vastaanotettavissa, 403 ja virheviestin jos henkilöllä estävä aikaisempi vastaanotto"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    tags swaggerGroupTag)
  get("/:hakuOid/hakemus/:hakemusOid/hakukohde/:hakukohdeOid/vastaanotettavuus", operation(getHakukohteenVastaanotettavuusSwagger)) {
    val hakemusOidString = params("hakemusOid")
    val hakukohdeOidString = params("hakukohdeOid")
    auditLog(Map("hakemusOid" -> hakemusOidString, "hakukohdeOid" -> hakukohdeOidString), HakemuksenLuku)
    Try(vastaanottoService.tarkistaVastaanotettavuus(HakemusOid(hakemusOidString), HakukohdeOid(hakukohdeOidString)))
      .map((_) => Ok())
      .recover({ case pae:PriorAcceptanceException => Forbidden("error" -> pae.getMessage) })
      .get
  }

  val postIlmoittautuminenSwagger: OperationBuilder = (apiOperation[Unit]("ilmoittaudu")
    summary "Tallenna hakukohteelle uusi ilmoittautumistila"
    // Real body param type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
    notes "Bodyssä tulee antaa tieto hakukohteen ilmoittautumistilan muutoksesta Ilmoittautuminen tyyppinä. Esim:\n" +
    pretty(Extraction.decompose(
      Ilmoittautuminen(
        HakukohdeOid("1.2.3.4"),
        LasnaKokoLukuvuosi,
        "henkilö: 5.5.5.5",
        "kuvaus mitä kautta muokkaus tehty"
      )
    )) + ".\nMahdolliset ilmoittautumistilat: " + IlmoittautumisTila.values().toList.map(_.toString)

    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid, jonka vastaanottotilaa ollaan muokkaamassa")
    tags swaggerGroupTag)
  post("/:hakuOid/hakemus/:hakemusOid/ilmoittaudu", operation(postIlmoittautuminenSwagger)) {
    val hakemusOidString = params("hakemusOid")
    val ilmoittautuminen = parsedBody.extract[Ilmoittautuminen]
    val auditParams: Map[String, String] = Map("hakuOid" -> params("hakuOid"), "hakemusOid" -> hakemusOidString)
    val addedParams: Map[String, String] = Map("ilmoittautumisTila" -> ilmoittautuminen.tila.toString)
    auditLogChanged(auditParams, IlmoittautumisTilanTallennus, addedParams, "added")
    ilmoittautumisService.ilmoittaudu(HakemusOid(hakemusOidString), ilmoittautuminen)
  }

  @Deprecated //Ei käytetä mistään? Ei tarvita hyväksymis/jälkiohjauskirjeitäkään varten!
  lazy val getHaunSijoitteluajonTuloksetSwagger: OperationBuilder = (apiOperation[Unit]("getHaunSijoitteluajonTuloksetSwagger")
    summary """Sivutettu listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required
    parameter queryParam[Boolean]("hyvaksytyt").description("Listaa jossakin kohteessa hyvaksytyt").optional
    parameter queryParam[Boolean]("ilmanHyvaksyntaa").description("Listaa henkilot jotka ovat taysin ilman hyvaksyntaa (missaan kohteessa)").optional
    parameter queryParam[Boolean]("vastaanottaneet").description("Listaa henkilot jotka ovat ottaneet paikan vastaan").optional
    parameter queryParam[List[String]]("hakukohdeOid").description("Rajoita hakua niin etta naytetaan hakijat jotka ovat jollain toiveella hakeneet naihin kohteisiin").optional
    parameter queryParam[Int]("count").description("Nayta n kappaletta tuloksia. Kayta sivutuksessa").optional
    parameter queryParam[Int]("index").description("Aloita nayttaminen kohdasta n. Kayta sivutuksessa.").optional
    tags swaggerGroupTag)
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(getHaunSijoitteluajonTuloksetSwagger)) {
    def booleanParam(n: String): Option[Boolean] = params.get(n).map(_.toBoolean)
    def intParam(n: String): Option[Int] = params.get(n).map(_.toInt)

    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val hyvaksytyt = booleanParam("hyvaksytyt")
    val ilmanHyvaksyntaa = booleanParam("ilmanHyvaksyntaa")
    val vastaanottaneet = booleanParam("vastaanottaneet")
    val hakukohdeOid = multiParams.get("hakukohdeOid").map(_.toList.map(HakukohdeOid))
    val count = intParam("count")
    val index = intParam("index")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(hakuOid, sijoitteluajoId, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOid, count, index)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getHakukohteenKaikkiHakijatSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenKaikkiHakijatSwagger")
    summary """Listaus haun hakukohteen kaikista hakijoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid").required
    tags swaggerGroupTag)
  get("/:hakuOid/hakukohde/:hakukohdeOid/hakijat", operation(getHakukohteenKaikkiHakijatSwagger)) {
    val hakuOidString = params("hakuOid")
    val hakukohdeOidString = params("hakukohdeOid")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(HakuOid(hakuOidString),
      "latest",
      hyvaksytyt = None,
      ilmanHyvaksyntaa = None,
      vastaanottaneet = None,
      hakukohdeOid = Some(List(HakukohdeOid(hakukohdeOidString))),
      count = None,
      index = None)
    auditLog(Map("hakuOid" -> hakuOidString, "hakukohdeOid" -> hakukohdeOidString), HakutietojenLuku)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getHaunIlmanHyvaksyntaaSwagger: OperationBuilder = (apiOperation[Unit]("getHaunIlmanHyvaksyntaaSwagger")
    summary """Listaus haun hakijoista, joilla ei ole koulutuspaikkaa (ilman hyväksyntää)"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    tags swaggerGroupTag)
  get("/:hakuOid/ilmanHyvaksyntaa", operation(getHaunIlmanHyvaksyntaaSwagger)) {
    val hakuOidString = params("hakuOid")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(HakuOid(hakuOidString),
      "latest",
      hyvaksytyt = None,
      ilmanHyvaksyntaa = Some(true),
      vastaanottaneet = None,
      hakukohdeOid = None,
      count = None,
      index = None)
    auditLog(Map("hakuOid" -> hakuOidString), HakutietojenLuku)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getHaunHyvaksytytSwagger: OperationBuilder = (apiOperation[Unit]("getHaunHyvaksytytSwagger")
    summary """Listaus haun hyväksytyistä hakijoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid").required
    tags swaggerGroupTag)
  get("/:hakuOid/hyvaksytyt", operation(getHaunHyvaksytytSwagger)) {
    val hakuOidString = params("hakuOid")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(HakuOid(hakuOidString),
      "latest",
      hyvaksytyt = Some(true),
      ilmanHyvaksyntaa = None,
      vastaanottaneet = None,
      hakukohdeOid = None,
      count = None,
      index = None)

    auditLog(Map("hakuOid" -> hakuOidString), HakutietojenLuku)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getHakukohteenHyvaksytytSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenHyvaksytytSwagger")
    summary """Listaus haun hakukohteen hyväksytyistä hakijoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid").required
    tags swaggerGroupTag)
  get("/:hakuOid/hakukohde/:hakukohdeOid/hyvaksytyt", operation(getHakukohteenHyvaksytytSwagger)) {
    val hakuOidString = params("hakuOid")
    val hakukohdeOidString = params("hakukohdeOid")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(HakuOid(hakuOidString),
      "latest",
      hyvaksytyt = Some(true),
      ilmanHyvaksyntaa = None,
      vastaanottaneet = None,
      hakukohdeOid = Some(List(HakukohdeOid(hakukohdeOidString))),
      count = None,
      index = None)
    auditLog(Map("hakuOid" -> hakuOidString, "hakukohdeOid" -> hakukohdeOidString), HakutietojenLuku)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getHakemuksenSijoitteluajonTulosSwagger: OperationBuilder = (apiOperation[Unit]("getHakemuksenSijoitteluajonTulosSwagger")
    summary """Näyttää yksittäisen hakemuksen kaikki hakutoiveet ja tiedot kaikista valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid").required
    tags swaggerGroupTag)
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemus/:hakemusOid", operation(getHakemuksenSijoitteluajonTulosSwagger)) {
    val hakuOidString = params("hakuOid")
    val sijoitteluajoIdString = params("sijoitteluajoId")
    val hakemusOidString = params("hakemusOid")
    auditLog(Map("hakuOid" -> hakuOidString, "sijoitteluajoId" -> sijoitteluajoIdString, "hakemusOid" -> hakemusOidString), HakemuksenLuku)
    valintatulosService.sijoittelunTulosHakemukselle(HakuOid(hakuOidString), sijoitteluajoIdString, HakemusOid(hakemusOidString)) match {
      case Some(hakijaDto) => Ok(JsonFormats.javaObjectToJsonString(hakijaDto))
      case None => Ok(JsonFormats.javaObjectToJsonString(new HakijaDTO()))
    }
  }

  lazy val getStreamingHaunSijoitteluajonTuloksetSwagger: OperationBuilder = (apiOperation[Unit]("getStreamingHaunSijoitteluajonTuloksetSwagger")
    summary """Streamaava listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required
    parameter queryParam[Boolean]("vainMerkitsevaJono").description("Jos true, palautetaan vain merkitsevän valintatapajonon tiedot").optional
    tags swaggerGroupTag)
  get("/streaming/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(getStreamingHaunSijoitteluajonTuloksetSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val vainMerkitsevaJono = params.get("vainMerkitsevaJono").map(_.toBoolean).getOrElse(false)

    writeSijoittelunTuloksetStreamingToResponse(
      response, hakuOid, w => streamingValintatulosService.streamSijoittelunTuloksetOfWholeHaku(hakuOid, sijoitteluajoId, w, vainMerkitsevaJono))
  }

  lazy val postStreamingHaunSijoitteluajonHakukohteidenTuloksetSwagger: OperationBuilder = (apiOperation[Unit]("postStreamingHaunSijoitteluajonHakukohteidenTuloksetSwagger")
    summary """Streamaava listaus annettujen hakukohteiden hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista annettuihin hakukohdeoideihin kohdistuneista hakutoiveista ja niiden valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required
    parameter queryParam[Boolean]("vainMerkitsevaJono").description("Jos true, palautetaan vain merkitsevän valintatapajonon tiedot").optional
    parameter bodyParam[Seq[String]]("hakukohdeOidit").description("Hakukohteet, joiden tulokset halutaan").required
    tags swaggerGroupTag)
  post("/streaming/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(postStreamingHaunSijoitteluajonHakukohteidenTuloksetSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val sijoitteluajoId = params("sijoitteluajoId")
    val vainMerkitsevaJono = params.get("vainMerkitsevaJono").exists(_.toBoolean)
    val hakukohdeOids = parsedBody.extract[Seq[String]]
    logger.info(s"Results of ${hakukohdeOids.size} hakukohde of haku $hakuOid were requested.")
    if (hakukohdeOids.isEmpty) {
      BadRequest("Anna kysyttävät hakukohdeoidit bodyssä.")
    } else {
      writeSijoittelunTuloksetStreamingToResponse(response, hakuOid, w => streamingValintatulosService.streamSijoittelunTuloksetOfHakukohdes(
        hakuOid, sijoitteluajoId, hakukohdeOids.toSet.map(HakukohdeOid), w, vainMerkitsevaJono))
    }
  }


  private def writeSijoittelunTuloksetStreamingToResponse(response: HttpServletResponse,
                                                          hakuOid: HakuOid,
                                                          writeWholeResultsToResponse: (HakijaDTO => Unit) => Unit): Unit = {
    val writer = response.writer

    writer.print("[")
    var index = 0
    try {
      val writeSingleResult: HakijaDTO => Unit = { hakijaDto =>
        if (index > 0) {
          writer.print(",")
        }
        writer.print(JsonFormats.javaObjectToJsonString(hakijaDto))
        index = index + 1
      }
      writeWholeResultsToResponse(writeSingleResult)
    } catch {
      case t: Throwable => throw new StreamingFailureException(t, s""", {"error": "${t.getMessage}"}] """)
    }
    logger.info(s"Returned $index ${classOf[HakijaDTO].getSimpleName} objects for haku $hakuOid")
    auditLog(Map("hakuOid" -> hakuOid.s), SijoitteluAjonTulostenLuku)
    writer.print("]")
  }

  private def serveStreamingResults(fetchData: => Option[Iterator[Hakemuksentulos]], info: String = ""): Any = {
    hakemustenTulosHakuLock.execute[Any](() => {
      fetchData match {
        case Some(tulos) => JsonStreamWriter.writeJsonStream(tulos, response.writer)
        case _ => NotFound("error" -> "Not found")
      }
    }, info) match {
      case Right(ok) => ok
      case Left(message) =>
        logger.error(message)
        TooManyRequests(message)
    }
  }

  def auditLog(auditParams: Map[String, String], auditOperation: Operation): Unit
  def auditLogChanged(auditParams: Map[String, String], auditOperation: Operation, auditParamsAdded: Map[String, String], changeOperation: String): Unit
}
