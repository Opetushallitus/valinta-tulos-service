package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.Operation
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.json.JsonStreamWriter
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.streamingresults.HakemustenTulosHakuLock
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, Kausi, PaatettavaOpiskeluOikeus, ValpasValinnantuloksetKysely}
import org.joda.time.DateTime
import org.json4s.Extraction
import org.json4s.jackson.Serialization.read
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.swagger._
import org.scalatra.{NotFound, Ok, TooManyRequests}

import java.util.Date

//TODO: Näitä rajapintoja kutsutaan vielä Suresta.
//      Koko servlet voidaan poistaa, kun Sure on sammutettu.
class PrivateValintatulosServlet(valintatulosService: ValintatulosService,
                                 hakemustenTulosHakuLock: HakemustenTulosHakuLock)
                                (implicit val swagger: Swagger,
                                 appConfig: VtsAppConfig) extends VtsServletBase {

  val applicationDescription = "Sisäinen valintatulosten REST API"

  val swaggerGroupTag = "valintatulos-private"

   def auditLog(auditParams: Map[String, String], auditOperation: Operation): Unit = {
    logger.info(s"PrivateValintatulosServlet REST call: $auditOperation with parameters: $auditParams")
  }

  def auditLogChanged(auditParams: Map[String, String], auditOperation: Operation, addedParams: Map[String, String], changeOperation: String): Unit = {
    logger.info(s"PrivateValintatulosServlet REST call: $auditOperation with parameters: $auditParams $changeOperation parameters: $addedParams")
  }

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
          yhteishaku = true,
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
    description ("Palauttaa tyyppiä Hakemuksentulos. Esim:\n" +
    pretty(Extraction.decompose(exampleHakemuksenTulos)))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    parameter pathParam[String]("hakemusOid").description("Hakemuksen oid, jonka tulokset halutaan")
    tags swaggerGroupTag)
  get("/:hakuOid/hakemus/:hakemusOid", operation(getHakemusSwagger)) {
    val hakemusOidString = params("hakemusOid")
    auditLog(Map("hakuOid" -> params("hakuOid"), "hakemusOid" -> hakemusOidString), HakemuksenLuku)
    valintatulosService.hakemuksentulos(HakemusOid(hakemusOidString)) match {
      case Some(tulos) => {
        try {
          val oikeudet = valintatulosService.haePaattyneetOpiskeluoikeudet(tulos)
          tulosWithOikeudet(tulos, oikeudet)
        } catch {
          case e: Exception =>
            logger.error(s"Virhe haettaessa näytettyjä päättyneitä opiskeluoikeuksia hakemukselle $hakemusOidString. Palautetaan tulokset.", e)
            tulos
        }
      }
      case _ => NotFound("error" -> "Not found")
    }
  }

  private def tulosWithOikeudet(tulos: Hakemuksentulos, oikeudet: List[(Hakutoiveentulos, Option[String])]): Hakemuksentulos = {
    val toiveet: List[Hakutoiveentulos] = oikeudet.map { case (toive, toiveenOikeudet) =>
      val parsitutOikeudet = toiveenOikeudet.map(o => parse(o).extract[List[PaatettavaOpiskeluOikeus]])
        .getOrElse(List.empty)
      toive.copy(naytetytPaatettavatOpiskeluoikeudet = parsitutOikeudet)
    }
    tulos.copy(hakutoiveet = toiveet)
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
    description ("Palauttaa tyyppiä Seq[Hakemuksentulos]. Esim:\n" +
    pretty(Extraction.decompose(Seq(exampleHakemuksenTulos))))
    parameter pathParam[String]("hakuOid").description("Haun oid")
    tags swaggerGroupTag)
  get("/:hakuOid", operation(getHakemuksetSwagger)) {
    val hakuOidString = params("hakuOid")
    val info = hakuOidString + "_" + System.currentTimeMillis()
    logger.info(s"getHakemuksetForHaku: $hakuOidString")
    auditLog(Map("hakuOid" -> hakuOidString), HakemuksenLuku)
    serveStreamingResults({ valintatulosService.hakemustenTulosByHaku(HakuOid(hakuOidString), false) }, info)
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
}
