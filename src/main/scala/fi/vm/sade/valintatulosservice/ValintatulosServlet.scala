package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.config.AppConfig.AppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.{JsonFormats, JsonStreamWriter, StreamingFailureException}
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, Hakuaika, YhdenPaikanSaanto}
import org.joda.time.DateTime
import org.json4s.Extraction
import org.scalatra._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

import scala.util.Try

abstract class ValintatulosServlet(valintatulosService: ValintatulosService, vastaanottoService: VastaanottoService, ilmoittautumisService: IlmoittautumisService)(implicit val swagger: Swagger, appConfig: AppConfig) extends VtsServletBase {

  lazy val exampleHakemuksenTulos = Hakemuksentulos(
    "2.2.2.2",
    "4.3.2.1",
    "1.3.3.1",
    Some(Vastaanottoaikataulu(Some(new DateTime()), Some(14))),
    List(
      Hakutoiveentulos.julkaistavaVersioSijoittelunTuloksesta(
        HakutoiveenSijoitteluntulos.kesken("1.2.3.4", "4.4.4.4"),
        Hakutoive("1.2.3.4", "4.4.4.4", "Hakukohde1", "Tarjoaja1"),
        Haku("5.5.5.5", true, true, true, false, true, None, Set(),
          List(Hakuaika("12345", Some(System.currentTimeMillis()), Some(System.currentTimeMillis()))),
          Kausi("2016S"),
          YhdenPaikanSaanto(false, "")),
        Some(Ohjausparametrit(None, Some(DateTime.now().plusDays(10)), Some(DateTime.now().plusDays(30)), Some(DateTime.now().plusDays(60)))))
    )
  )

  // Real return type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
  lazy val getHakemusSwagger: OperationBuilder = (apiOperation[Unit]("getHakemus")
    summary "Hae hakemuksen tulokset."
    notes "Palauttaa tyyppiä Hakemuksentulos. Esim:\n" +
      pretty(Extraction.decompose(exampleHakemuksenTulos))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid, jonka tulokset halutaan")
  )
  get("/:hakuOid/hakemus/:hakemusOid", operation(getHakemusSwagger)) {
    val hakuOid = params("hakuOid")
    val hakemusOid = params("hakemusOid")
    valintatulosService.hakemuksentulos(hakuOid, hakemusOid) match {
      case Some(tulos) => tulos
      case _ => NotFound("error" -> "Not found")
    }
  }

  lazy val getHakemuksetSwagger: OperationBuilder = (apiOperation[Unit]("getHakemukset")
    summary "Hae haun kaikkien hakemusten tulokset."
    notes "Palauttaa tyyppiä Seq[Hakemuksentulos]. Esim:\n" +
      pretty(Extraction.decompose(Seq(exampleHakemuksenTulos)))
    parameter pathParam[String]("hakuOid").description("Haun oid")
  )
  get("/:hakuOid", operation(getHakemuksetSwagger)) {
    val hakuOid = params("hakuOid")
    serveStreamingResults({ valintatulosService.hakemustenTulosByHaku(hakuOid) })
  }

  get("/:hakuOid/hakukohde/:hakukohdeOid", operation(getHakukohteenHakemuksetSwagger)) {
    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")
    serveStreamingResults({ valintatulosService.hakemustenTulosByHakukohde(hakuOid, hakukohdeOid) })
  }

  lazy val getHakukohteenHakemuksetSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenHakemukset")
    summary "Hae hakukohteen kaikkien hakemusten tulokset."
    notes "Palauttaa tyyppiä Seq[Hakemuksentulos]. Esim:\n" +
    pretty(Extraction.decompose(Seq(exampleHakemuksenTulos)))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
  )

  lazy val getHakukohteenVastaanotettavuusSwagger: OperationBuilder = (apiOperation[Unit]("getHakukohteenHakemukset")
    summary "Palauttaa 200 jos hakutoive vastaanotettavissa, 403 ja virheviestin jos henkilöllä estävä aikaisempi vastaanotto"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    )
  get("/:hakuOid/hakemus/:hakemusOid/hakukohde/:hakukohdeOid/vastaanotettavuus", operation(getHakukohteenVastaanotettavuusSwagger)) {
    Try(vastaanottoService.tarkistaVastaanotettavuus(params("hakemusOid"), params("hakukohdeOid")))
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
        "1.2.3.4",
        Ilmoittautumistila.läsnä_koko_lukuvuosi,
        "henkilö: 5.5.5.5",
        "kuvaus mitä kautta muokkaus tehty"
      )
    )) + ".\nMahdolliset ilmoittautumistilat: " + Ilmoittautumistila.values

    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid, jonka vastaanottotilaa ollaan muokkaamassa")
    )
  post("/:hakuOid/hakemus/:hakemusOid/ilmoittaudu", operation(postIlmoittautuminenSwagger)) {
    val hakuOid = params("hakuOid")
    val hakemusOid = params("hakemusOid")
    val ilmoittautuminen = parsedBody.extract[Ilmoittautuminen]

    ilmoittautumisService.ilmoittaudu(hakuOid, hakemusOid, ilmoittautuminen)
  }

  lazy val getHaunSijoitteluajonTuloksetSwagger: OperationBuilder = (apiOperation[Unit]("getHaunSijoitteluajonTuloksetSwagger")
    summary """Sivutettu listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required
    parameter queryParam[Boolean]("hyvaksytyt").description("Listaa jossakin kohteessa hyvaksytyt").optional
    parameter queryParam[Boolean]("ilmanHyvaksyntaa").description("Listaa henkilot jotka ovat taysin ilman hyvaksyntaa (missaan kohteessa)").optional
    parameter queryParam[Boolean]("vastaanottaneet").description("Listaa henkilot jotka ovat ottaneet paikan vastaan").optional
    parameter queryParam[List[String]]("hakukohdeOid").description("Rajoita hakua niin etta naytetaan hakijat jotka ovat jollain toiveella hakeneet naihin kohteisiin").optional
    parameter queryParam[Int]("count").description("Nayta n kappaletta tuloksia. Kayta sivutuksessa").optional
    parameter queryParam[Int]("index").description("Aloita nayttaminen kohdasta n. Kayta sivutuksessa.").optional)
  get("/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(getHaunSijoitteluajonTuloksetSwagger)) {
    def booleanParam(n: String): Option[Boolean] = params.get(n).map(_.toBoolean)
    def intParam(n: String): Option[Int] = params.get(n).map(_.toInt)

    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")
    val hyvaksytyt = booleanParam("hyvaksytyt")
    val ilmanHyvaksyntaa = booleanParam("ilmanHyvaksyntaa")
    val vastaanottaneet = booleanParam("vastaanottaneet")
    val hakukohdeOid = multiParams.get("hakukohdeOid").map(_.toList)
    val count = intParam("count")
    val index = intParam("index")
    val hakijaPaginationObject = valintatulosService.sijoittelunTulokset(hakuOid, sijoitteluajoId, hyvaksytyt, ilmanHyvaksyntaa, vastaanottaneet, hakukohdeOid, count, index)
    Ok(JsonFormats.javaObjectToJsonString(hakijaPaginationObject))
  }

  lazy val getStreamingHaunSijoitteluajonTuloksetSwagger: OperationBuilder = (apiOperation[Unit]("getStreamingHaunSijoitteluajonTuloksetSwagger")
    summary """Streamaava listaus hakemuksien/hakijoiden listaukseen. Yksityiskohtainen listaus kaikista hakutoiveista ja niiden valintatapajonoista"""
    parameter pathParam[String]("hakuOid").description("Haun oid").required
    parameter pathParam[String]("sijoitteluajoId").description("""Sijoitteluajon id tai "latest"""").required)
  get("/streaming/:hakuOid/sijoitteluajo/:sijoitteluajoId/hakemukset", operation(getHaunSijoitteluajonTuloksetSwagger)) {
    val hakuOid = params("hakuOid")
    val sijoitteluajoId = params("sijoitteluajoId")

    val writer = response.writer

    writer.print("[")
    var index = 0
    try {
      val writeResult: HakijaDTO => Unit = { hakijaDto =>
        if (index > 0) {
          writer.print(",")
        }
        writer.print(JsonFormats.javaObjectToJsonString(hakijaDto))
        index = index + 1
      }
      valintatulosService.streamSijoittelunTulokset(hakuOid, sijoitteluajoId, writeResult)
    } catch {
      case t: Throwable => throw new StreamingFailureException(t, s""", {"error": "${t.getMessage}"}] """)
    }
    logger.info(s"Returned $index ${classOf[HakijaDTO].getSimpleName} objects for haku $hakuOid")
    writer.print("]")
  }

  private def serveStreamingResults(fetchData: => Option[Iterator[Hakemuksentulos]]): Any = {
    HakemustenTulosHakuLock.synchronized {
      fetchData match {
        case Some(tulos) => JsonStreamWriter.writeJsonStream(tulos, response.writer)
        case _ => NotFound("error" -> "Not found")
      }
    }
  }
}
