package fi.vm.sade.valintatulosservice.suorituspalvelu

import fi.vm.sade.javautils.nio.cas.{CasClient, CasClientBuilder}
import fi.vm.sade.security.ScalaCasConfig
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakijaOid, HakuOid, HakukohdeOid, PaatettavaOpiskeluOikeus}
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.utils.TimeUtils
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import org.asynchttpclient.RequestBuilder
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization

import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class UudelleenpaattelynKohde(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid)

case class UudelleenpaattelynTulos(hakemusOid: HakemusOid,
                                   hakukohdeOid: HakukohdeOid,
                                   tila: String,
                                   syy: Option[String] = None,
                                   aiemmat: Option[List[PaatettavaOpiskeluOikeus]] = None,
                                   paatellyt: Option[List[PaatettavaOpiskeluOikeus]] = None,
                                   paateltyAloitusPvm: Option[String] = None)

object UudelleenpaattelynTila {
  val Tallennettu = "TALLENNETTU"
  val Kuivaajo = "KUIVAAJO"
  val Ohitettu = "OHITETTU"
  val Virhe = "VIRHE"
}

class SuorituspalveluService(config: VtsAppConfig, hakuService: HakuService, client: CasClient, db: ValintarekisteriDb) extends JsonFormats with Logging {

  def getPaatettavatOpiskeluOikeudet(hakijaOid: HakijaOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): List[PaatettavaOpiskeluOikeus] = {
    haePaatettavatOpiskeluOikeudet(hakijaOid, hakuOid, hakukohdeOid) match {
      case Left(e) =>
        logger.error(
          s"Virhe päättyvien opiskeluoikeuksien hakemisessa, hakijaOid=$hakijaOid, hakuOid=$hakuOid, hakukohdeOid=$hakukohdeOid: ${e.getMessage}"
        )
        List.empty
      case Right(o) =>
        o
    }
  }

  def haePaatettavatOpiskeluOikeudet(hakijaOid: HakijaOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): Either[Throwable, List[PaatettavaOpiskeluOikeus]] = {
    logger.info(s"Haetaan päättyvät opiskeluoikeudet hakijalle $hakijaOid, haulle $hakuOid, hakukohteelle $hakukohdeOid")
    val url =
      s"${config.ophUrlProperties.url("url-suorituspalvelu")}/api/v1/yos/hakija/$hakijaOid/haku/$hakuOid/hakukohde/$hakukohdeOid/opiskeluoikeudet"
    fetchOikeudet(url)
  }

  def getAndStorePaatettavatOpiskeluOikeudet(hakijaOid: HakijaOid, hakuOid: HakuOid, hakukohdeOid: HakukohdeOid, hakemusOid: HakemusOid): List[PaatettavaOpiskeluOikeus] = {
    hakuService.getHakukohde(hakukohdeOid).fold(
      e => {
        logger.warn(s"Hakukohdetta $hakukohdeOid ei saatu haettua hakiessa päätettäviä opiskeluoikeuksia hakemukselle $hakemusOid", e)
        List.empty
      }, hakukohde => {
        val oikeudet = getPaatettavatOpiskeluOikeudet(hakijaOid, hakuOid, hakukohdeOid)
        if (oikeudet.nonEmpty) {
          val alkuPvm = paatteleAloitusajankohta(hakukohde)
          db.storePaatetettavatOpiskeluOikeudet(hakijaOid.toString, hakukohdeOid, hakemusOid, alkuPvm, Serialization.write(oikeudet))
        }
        oikeudet
      }
    )
  }

  // Päättelee päätettävät opiskeluoikeudet uudelleen suorituspalvelusta ja ylikirjoittaa tallennetut tiedot.
  // Käsitellään vain hakutoiveet, joilla on sitova tai ehdollinen vastaanotto ja jotka kuuluvat YOS:n piiriin.
  def paatteleUudelleen(kohteet: List[UudelleenpaattelynKohde], dryRun: Boolean): List[UudelleenpaattelynTulos] = {
    kohteet.map(kohde => {
      val tulos = try {
        paatteleUudelleen(kohde, dryRun)
      } catch {
        case e: Exception =>
          logger.error(s"Päätettävien opiskeluoikeuksien uudelleenpäättely epäonnistui hakemukselle ${kohde.hakemusOid} ja hakukohteelle ${kohde.hakukohdeOid}", e)
          UudelleenpaattelynTulos(kohde.hakemusOid, kohde.hakukohdeOid, UudelleenpaattelynTila.Virhe, Some(e.getMessage))
      }
      logger.info(s"Päätettävien opiskeluoikeuksien uudelleenpäättely (dryRun=$dryRun): $tulos")
      tulos
    })
  }

  private def paatteleUudelleen(kohde: UudelleenpaattelynKohde, dryRun: Boolean): UudelleenpaattelynTulos = {
    val UudelleenpaattelynKohde(hakemusOid, hakukohdeOid) = kohde
    def ohitettu(syy: String) = UudelleenpaattelynTulos(hakemusOid, hakukohdeOid, UudelleenpaattelynTila.Ohitettu, Some(syy))
    def virhe(syy: String) = UudelleenpaattelynTulos(hakemusOid, hakukohdeOid, UudelleenpaattelynTila.Virhe, Some(syy))

    db.runBlocking(db.findSitovanTaiEhdollisenVastaanotonHenkilo(hakemusOid, hakukohdeOid)) match {
      case None =>
        ohitettu("Hakutoiveelle ei löydy sitovaa tai ehdollista vastaanottoa")
      case Some(henkiloOid) =>
        hakuService.getHakukohde(hakukohdeOid) match {
          case Left(e) =>
            virhe(s"Hakukohteen haku epäonnistui: ${e.getMessage}")
          case Right(hakukohde) if !hakukohde.kuuluuYosinPiiriin =>
            ohitettu("Hakukohde ei kuulu YOS:n piiriin")
          case Right(hakukohde) =>
            haePaatettavatOpiskeluOikeudet(HakijaOid(henkiloOid), hakukohde.hakuOid, hakukohdeOid) match {
              case Left(e) =>
                virhe(s"Päätettävien opiskeluoikeuksien haku suorituspalvelusta epäonnistui: ${e.getMessage}")
              case Right(oikeudet) =>
                val alkuPvm = paatteleAloitusajankohta(hakukohde)
                val aiemmat = db.runBlocking(db.findHakemuksenVastaanotonPaatettavatOpiskeluOikeudet(hakemusOid, hakukohdeOid))
                  .map(parse(_).extract[List[PaatettavaOpiskeluOikeus]])
                if (!dryRun) {
                  db.korvaaPaatettavatOpiskeluOikeudet(henkiloOid, hakukohdeOid, hakemusOid, alkuPvm, Serialization.write(oikeudet))
                }
                UudelleenpaattelynTulos(
                  hakemusOid,
                  hakukohdeOid,
                  if (dryRun) UudelleenpaattelynTila.Kuivaajo else UudelleenpaattelynTila.Tallennettu,
                  aiemmat = aiemmat,
                  paatellyt = Some(oikeudet),
                  paateltyAloitusPvm = Option(alkuPvm))
            }
        }
    }
  }

  private def paatteleAloitusajankohta(hakukohde: Hakukohde): String = {
    val alkuPvm = try {
      TimeUtils.getPaateltyAloitusajankohta(hakukohde)
    } catch {
      case e: Exception =>
        logger.warn(s"Hakukohteen ${hakukohde.oid} aloitusajankohdan päättely epäonnistui", e)
        null
    }
    if (alkuPvm == null) {
      logger.warn(
        s"Hakukohteelle ${hakukohde.oid} ei pystytty päättelemään aloitusajankohtaa"
      )
    }
    alkuPvm
  }

  private def fetchOikeudet(url: String): Either[Throwable, List[PaatettavaOpiskeluOikeus]] = {
    val req = new RequestBuilder().setMethod("GET").setUrl(url).build()
    val result = toScala(client.execute(req)).map {
      case r if r.getStatusCode == 200 =>
        Right(parse(r.getResponseBody).extract[List[PaatettavaOpiskeluOikeus]])
      case r =>
        val message = s"GET $url failed with status ${r.getStatusCode}: ${r.getResponseBody}"
        if (r.getStatusCode == 404) {
          throw new IllegalArgumentException(message)
        } else {
          throw new RuntimeException(message)
        }
    }

    try {
      Await.result(result, Duration(1, TimeUnit.MINUTES))
    } catch {
      case e: Throwable => Left(e)
    }
  }
}
