package fi.vm.sade.valintatulosservice.ensikertalaisuus

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet._
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.EnsikertalaisuusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ensikertalaisuus, Kausi, VastaanottoHistoria}
import fi.vm.sade.valintatulosservice.{VtsServletBase, VtsSwaggerBase}
import org.json4s.Formats
import org.json4s.jackson.Serialization.read
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._

class EnsikertalaisuusServlet(ensikertalaisuusRepository: EnsikertalaisuusRepository, maxHenkiloOids: Int)(implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends VtsServletBase with EnsikertalaisuusSwagger {
  override implicit val jsonFormats: Formats = EnsikertalaisuusServlet.ensikertalaisuusJsonFormats

  def henkiloOid(oid: String): String = {
    require(oid.startsWith("1.2.246.562.24."), "Illegal henkilo oid")
    oid
  }

  get("/:henkilo", operation(getEnsikertalaisuusSwagger)) {
    ensikertalaisuusRepository.findEnsikertalaisuus(henkiloOid(params("henkilo")), parseKausi(params("koulutuksenAlkamiskausi")))
  }

  get("/:henkilo/historia", operation(getVastaanottoHistoriaSwagger)) {
    ensikertalaisuusRepository.findVastaanottoHistory(henkiloOid(params("henkilo")))
  }

  post("/", operation(postEnsikertalaisuusSwagger)) {
    val henkilot = read[Set[String]](request.body).map(henkiloOid)
    if (henkilot.size > maxHenkiloOids) throw new IllegalArgumentException(s"Too many henkilo oids (limit is $maxHenkiloOids, got ${henkilot.size})")

    ensikertalaisuusRepository.findEnsikertalaisuus(henkilot, parseKausi(params("koulutuksenAlkamiskausi")))
  }
}

object EnsikertalaisuusServlet {
  val ensikertalaisuusJsonFormats: Formats = JsonFormats.jsonFormats

  def parseKausi(d: String): Kausi = Kausi(d)
}

trait EnsikertalaisuusSwagger extends VtsSwaggerBase { this: SwaggerSupport =>
  override val applicationName = Some("ensikertalaisuus")
  override protected def applicationDescription: String = "Ensikertalaisuus-tiedon vastaanotto-osuus"

  registerModel(Model("Ensikertalaisuus", "Ensikertalaisuus", None, None,
    List("personOid" -> ModelProperty(DataType.String, required = true), "paattyi" -> ModelProperty(DataType.DateTime)),
    Some("Ensikertalaisuus"), None
  ))

  val getEnsikertalaisuusSwagger: OperationBuilder = apiOperation[Ensikertalaisuus]("getEnsikertalaisuus")
    .summary("Hae ensikertalaisuus-tiedon vastaanotto-osuus")
    .notes("Ei pidä käyttää sellaisenaan, vain ainoastaan suoritusrekisterin kautta. Rajapinta palauttaa joko pelkän " +
      "henkilo oidin tai henkilo oidin ja päivämäärän, jolloin ensikertalaisuus päättyi.")
    .parameter(pathParam[String]("henkiloOid").description("Henkilön oid").required)
    .parameter(queryParam[String]("koulutuksenAlkamiskausi")
      .description("Koulutuksen alkamiskausi, josta lähtien alkavat koulutukset kyselyssä otetaan huomioon (esim. 2016S").required)
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))

  val getVastaanottoHistoriaSwagger: OperationBuilder = apiOperation[VastaanottoHistoria]("getVastaanottoHistoria")
    .summary("Hae henkilön kk vastaanotto historia")
    .notes("Palauttaa vain korkeakoulupaikkojen vastaanotot.")
    .parameter(pathParam[String]("henkiloOid").description("Henkilön oid").required)
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))

  val postEnsikertalaisuusSwagger: OperationBuilder = apiOperation[Seq[Ensikertalaisuus]]("getEnsikertalaisuus")
    .summary("Hae ensikertalaisuus-tiedon vastaanotto-osuus")
    .notes("Ei pidä käyttää sellaisenaan, vain ainoastaan suoritusrekisterin kautta. Rajapinta palauttaa setin ensikertalaisuus-objekteja.")
    .parameter(bodyParam[Seq[String]]("henkiloOids")
      .description("Henkilöiden oidit json-sekvenssinä, enintään 10000 oidia yhdessä pyynnössä").required)
    .parameter(queryParam[String]("koulutuksenAlkamiskausi")
      .description("Koulutuksen alkamiskausi, josta lähtien alkavat koulutukset kyselyssä otetaan huomioon (esim. 2016S").required)
    .responseMessage(ModelResponseMessage(400, "Kuvaus virheellisestä pyynnöstä"))
    .responseMessage(ModelResponseMessage(500, "Virhe palvelussa"))
}

