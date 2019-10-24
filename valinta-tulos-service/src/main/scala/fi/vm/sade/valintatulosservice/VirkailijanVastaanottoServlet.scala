package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonFormats.javaObjectToJsonString
import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, PriorAcceptanceException, ValintatapajonoOid, VastaanottoEventDto, Vastaanottotila}
import org.joda.time.DateTime
import org.json4s.jackson.Serialization._
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{Forbidden, Ok}
import scala.collection.JavaConverters._

class VirkailijanVastaanottoServlet(valintatulosService: ValintatulosService, vastaanottoService: VastaanottoService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {

  override protected def applicationDescription: String = "Virkailijan vastaanottotietojen käsittely REST API"

  val getVastaanottoTilatByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getVastaanottoTilatByHakukohde")
    summary "Hakee vastaanoton tilat hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    tags "virkailija")
  get("/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getVastaanottoTilatByHakukohdeSwagger)) {

    val hakuOid = HakuOid(params("hakuOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))

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
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid")
    tags "virkailija")
  get("/valintatulos/haku/:hakuOid/hakemus/:hakemusOid", operation(getValintatuloksetByHakemusSwagger)) {
    val hakemusOid = HakemusOid(params("hakemusOid"))
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailijaByHakemus(hakemusOid).asJava))
  }

  val getValintatuloksetByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHakukohde")
    summary "Hakee valintatulokset hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    tags "virkailija")
  get("/valintatulos/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getValintatuloksetByHakukohdeSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailija(hakuOid, hakukohdeOid).asJava))
  }

  val getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger")
    summary "Hakee valintatulokset hakukohteen hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    tags "virkailija")
  get("/valintatulos/ilmanhakijantilaa/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(getValintatuloksetWithoutTilaHakijalleByHakukohdeSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailijaWithoutTilaHakijalle(hakuOid, hakukohdeOid).asJava))
  }

  val postLatenessFlagsForApplicationsSwagger: OperationBuilder = (apiOperation[Set[VastaanottoAikarajaMennyt]]("getLatenessFlagsForApplicationsSwagger")
    summary "Hakee annetuille hakijoille tiedon siitä onko vastaanotto myöhässä tähän hakukohteeseen"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter bodyParam[Set[String]]("hakemusOids").description("Kiinnostavien hakemusten oidit")
    tags "virkailija")
  post("/myohastyneet/haku/:hakuOid/hakukohde/:hakukohdeOid", operation(postLatenessFlagsForApplicationsSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val hakemusOids = read[Set[HakemusOid]](request.body)
    Ok(valintatulosService.haeVastaanotonAikarajaTiedot(hakuOid, hakukohdeOid, hakemusOids))
  }

  val postTilaHakijalleForApplicationsSwagger: OperationBuilder = (apiOperation[Set[TilaHakijalle]]("postTilaHakijalleForApplicationsSwagger")
    summary "Hakee annetuille hakijoille tiedon siitä onko vastaanotto myöhässä tähän hakukohteeseen"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakukohdeOid").description("Hakukohteen oid")
    parameter pathParam[String]("valintatapajonoOid").description("Valintatapajonon oid")
    parameter bodyParam[Set[String]]("hakemusOids").description("Kiinnostavien hakemusten oidit")
    tags "virkailija")
  post("/tilahakijalle/haku/:hakuOid/hakukohde/:hakukohdeOid/valintatapajono/:valintatapajonoOid", operation(postTilaHakijalleForApplicationsSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    val hakukohdeOid = HakukohdeOid(params("hakukohdeOid"))
    val valintatapajonoOid = ValintatapajonoOid(params("valintatapajonoOid"))
    val hakemusOids = read[Set[HakemusOid]](request.body)
    Ok(valintatulosService.haeTilatHakijoille(hakuOid, hakukohdeOid, valintatapajonoOid, hakemusOids))
  }

  val getValintatuloksetByHakuSwagger: OperationBuilder = (apiOperation[Unit]("getValintatuloksetByHaku")
    summary "Hakee valintatulokset haun hakijoille"
    parameter pathParam[String]("hakuOid").description("Haun oid")
    tags "virkailija")
  get("/valintatulos/haku/:hakuOid", operation(getValintatuloksetByHakuSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
    Ok(javaObjectToJsonString(valintatulosService.findValintaTuloksetForVirkailija(hakuOid).asJava))
  }

  val getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissaSwagger: OperationBuilder =
    (apiOperation[List[VastaanottoRecord]]("getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissa")
      summary "Yhden paikan säädöksen piirissä olevat vastaanotot annetun haun koulutuksen alkamiskaudella"
      parameter pathParam[String]("hakuOid").description("Haun oid")
      tags "virkailija")
  get("/vastaanotot/haku/:hakuOid", operation(getHaunKoulutuksenAlkamiskaudenVastaanototYhdenPaikanSaadoksenPiirissaSwagger)) {
    val hakuOid = HakuOid(params("hakuOid"))
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
    parameter bodyParam[List[VastaanottoEventDto]]
    tags "virkailija")
  post("/vastaanotto", operation(postVirkailijanVastaanottoActionsSwagger)) {

    val vastaanottoEvents = parsedBody.extract[List[VastaanottoEventDto]]
    vastaanottoService.vastaanotaVirkailijana(vastaanottoEvents)
  }

  val postTransactionalVirkailijanVastaanottoActionsSwagger: OperationBuilder = (apiOperation[Unit]("postVastaanotto")
    summary "Tallenna vastaanottotapahtumat transaktiossa"
    parameter bodyParam[List[VastaanottoEventDto]]
    tags "virkailija")
  post("/transactional-vastaanotto", operation(postTransactionalVirkailijanVastaanottoActionsSwagger)) {
    val vastaanottoEvents = parsedBody.extract[List[VastaanottoEventDto]]
    vastaanottoService.vastaanotaVirkailijanaInTransaction(vastaanottoEvents).get
  }
}

case class Result(status: Int, message: Option[String])
case class VastaanottoResult(henkiloOid: String, hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, result: Result)
case class VastaanottoAikarajaMennyt(hakemusOid: HakemusOid, mennyt: Boolean, vastaanottoDeadline: Option[DateTime])
case class TilaHakijalle(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, tilaHakijalle: String)
