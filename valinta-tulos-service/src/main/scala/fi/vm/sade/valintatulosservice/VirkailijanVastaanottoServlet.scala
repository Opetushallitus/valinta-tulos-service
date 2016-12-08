package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats.javaObjectToJsonString
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{PriorAcceptanceException, VastaanottoEventDto, Vastaanottotila}
import org.joda.time.DateTime
import org.json4s.jackson.Serialization._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{Forbidden, Ok}

class VirkailijanVastaanottoServlet(valintatulosService: ValintatulosService, vastaanottoService: VastaanottoService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {

  override val applicationName = Some("virkailija")

  override protected def applicationDescription: String = "Virkailijan vastaanottotietojen käsittely REST API"

  val getVastaanottoTilatByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getVastaanottoTilatByHakukohde")
    summary "Hakee vastaanoton tilat hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid"))
  get("/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getVastaanottoTilatByHakukohdeSwagger)) {

    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")

    valintatulosService.hakemustenTulosByHakukohde(hakuOid, hakukohdeOid).right.map(
      h => Ok(h.map(t => {
        val hakutoive = t.findHakutoive(hakukohdeOid).map(_._1)
        HakemuksenVastaanottotila(t.hakemusOid, hakutoive.map(_.valintatapajonoOid), hakutoive.map(_.vastaanottotila))
      }).toList)
    ) match {
      case Right(x) => x
      case Left(pae: PriorAcceptanceException) => Forbidden("error" -> pae.getMessage)
      case Left(e) => throw e
    }
  }

  val getValintatuloksetByHakemusSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHakemus")
    summary "Hakee hakemuksen valintatulokset hakukohteeseen"
    parameter pathParam[String]("hakuOid").description("Haku oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid"))
  get("/valintatulos/haku/:hakuOid/hakemus/:hakemusOid", operation(getValintatuloksetByHakemusSwagger)) {
    val hakemusOid = params("hakemusOid")
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailijaByHakemus(hakemusOid)))
  }

  val getValintatuloksetByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHakukohde")
    summary "Hakee valintatulokset hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid"))
  get("/valintatulos/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getValintatuloksetByHakukohdeSwagger)) {
    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailija(hakuOid, hakukohdeOid)))
  }

  val getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger")
    summary "Hakee valintatulokset hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid"))
  get("/valintatulos/ilmanhakijantilaa/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger)) {
    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailijaWithoutTilaHakijalle(hakuOid, hakukohdeOid)))
  }

  val postLatenessFlagsForApplicationsSwagger: OperationBuilder = (apiOperation[Set[VastaanottoAikarajaMennyt]]("getLatenessFlagsForApplicationsSwagger")
    summary "Hakee annetuille hakijoille tiedon siitä onko vastaanotto myöhässä tähän hakukohteeseen"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter bodyParam[Set[String]]("hakemusOids").description("Kiinnostavien hakemusten oidit"))
  post("/myohastyneet/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(postLatenessFlagsForApplicationsSwagger)) {
    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")
    val hakemusOids = read[Set[String]](request.body)
    Ok(valintatulosService.haeVastaanotonAikarajaTiedot(hakuOid, hakukohdeOid, hakemusOids))
  }

  val postTilaHakijalleForApplicationsSwagger: OperationBuilder = (apiOperation[Set[TilaHakijalle]]("postTilaHakijalleForApplicationsSwagger")
    summary "Hakee annetuille hakijoille tiedon siitä onko vastaanotto myöhässä tähän hakukohteeseen"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon oid")
    parameter bodyParam[Set[String]]("hakemusOids").description("Kiinnostavien hakemusten oidit"))
  post("/tilahakijalle/haku/:hakuOid/hakukohde/:hakukohdeOid/valintatapajono/:valintatapajonoOid", operation(postTilaHakijalleForApplicationsSwagger)) {
    val hakuOid = params("hakuOid")
    val hakukohdeOid = params("hakukohdeOid")
    val valintatapajonoOid = params("valintatapajonoOid")
    val hakemusOids = read[Set[String]](request.body)
    Ok(valintatulosService.haeTilatHakijoille(hakuOid, hakukohdeOid, valintatapajonoOid, hakemusOids))
  }

  val getValintatuloksetByHakuSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHaku")
    summary "Hakee valintatulokset haun hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid"))
  get("/valintatulos/haku/:hakuOid", operation(getValintatuloksetByHakuSwagger)) {
    val hakuOid = params("hakuOid")
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailija(hakuOid)))
  }

  val getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissaSwagger: OperationBuilder =
    (apiOperation[List[VastaanottoRecord]]("getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa")
      summary "Yhden paikan säädöksen piirissä olevat vastaanotot annetun haun koulutuksen alkamiskaudella"
      parameter pathParam[String]("hakuOid").description("Haun oid"))
  get("/vastaanotot/haku/:hakuOid", operation(getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissaSwagger)) {
    val hakuOid = params("hakuOid")
    Ok(valintatulosService.haunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa(hakuOid).toList)
  }

  val vastaanottoEventModel = Model(
    id = classOf[VastaanottoEventDto].getSimpleName,
    name = classOf[VastaanottoEventDto].getSimpleName,
    properties = List(
      "valintatapajonoOid" -> ModelProperty(`type` = DataType.String, required = true),
      "henkiloOid" -> ModelProperty(`type` = DataType.String, required = true),
      "hakemusOid" -> ModelProperty(`type` = DataType.String, required = true),
      "hakukohdeOid" -> ModelProperty(`type` = DataType.String, required = true),
      "hakuOid" -> ModelProperty(`type` = DataType.String, required = true),
      "ilmoittaja" -> ModelProperty(`type` = DataType.String, required = true),
      "tila" -> ModelProperty(`type` = DataType.String, required = true, allowableValues = AllowableValues(Vastaanottotila.values.toList)),
      "selite" -> ModelProperty(`type` = DataType.String, required = true)
    ))
  registerModel(vastaanottoEventModel)

  val postVirkailijanVastaanottoActionsSwagger: OperationBuilder = (apiOperation[List[VastaanottoResult]]("postVastaanotto")
    summary "Tallenna vastaanottotapahtumat"
    parameter bodyParam[List[VastaanottoEventDto]])
  post("/vastaanotto", operation(postVirkailijanVastaanottoActionsSwagger)) {

    val vastaanottoEvents = parsedBody.extract[List[VastaanottoEventDto]]
    vastaanottoService.vastaanotaVirkailijana(vastaanottoEvents)
  }

  val postTransactionalVirkailijanVastaanottoActionsSwagger: OperationBuilder = (apiOperation[Unit]("postVastaanotto")
    summary "Tallenna vastaanottotapahtumat transaktiossa"
    parameter bodyParam[List[VastaanottoEventDto]])
  post("/transactional-vastaanotto", operation(postTransactionalVirkailijanVastaanottoActionsSwagger)) {

    val vastaanottoEvents = parsedBody.extract[List[VastaanottoEventDto]]
    vastaanottoService.vastaanotaVirkailijanaInTransaction(vastaanottoEvents).get
  }
}

case class Result(status: Int, message: Option[String])
case class VastaanottoResult(henkiloOid: String, hakemusOid: String, hakukohdeOid: String, result: Result)
case class VastaanottoAikarajaMennyt(hakemusOid: String, mennyt: Boolean, vastaanottoDeadline: Option[DateTime])
case class TilaHakijalle(hakemusOid: String, hakukohdeOid: String, valintatapajonoOid: String, tilaHakijalle: String)
