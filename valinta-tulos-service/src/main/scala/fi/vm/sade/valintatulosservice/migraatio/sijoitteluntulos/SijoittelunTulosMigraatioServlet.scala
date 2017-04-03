package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.io.{PrintWriter, StringWriter}

import fi.vm.sade.valintatulosservice.VtsServletBase
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
<<<<<<< 7d5ce24e921769e961796577433ddb36e91f56b1
=======
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosBatchRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
>>>>>>> OK-119 : Put batch code to separate class
import org.json4s.jackson.Serialization.read
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{InternalServerError, Ok}

import scala.util.{Failure, Try}

class SijoittelunTulosMigraatioServlet(sijoitteluRepository: SijoitteluRepository,
                                       valinnantulosBatchRepository: ValinnantulosBatchRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       tarjontaHakuService: TarjontaHakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("sijoittelun-tulos-migraatio")

  override protected def applicationDescription: String = "REST-API sijoittelun tuloksien migroinniksi valintarekisteriin"

  private val sijoittelunTulosRestClient = new SijoittelunTulosRestClient(appConfig)
  private val migraatioService = new SijoitteluntulosMigraatioService(sijoittelunTulosRestClient, appConfig,
    sijoitteluRepository, valinnantulosBatchRepository, hakukohdeRecordService, tarjontaHakuService, valintalaskentakoostepalveluService)

  logger.warn("Mountataan Valintarekisterin sijoittelun tuloksien migraatioservlet!")

  val postHakuMigration: OperationBuilder = (apiOperation[String]("migroiHakukohde")
    summary "Migroi sijoitteludb:stä valintarekisteriin hakuja. Toistaiseksi ei välitä siitä, ovatko tiedot muuttuneet"
    parameter queryParam[Boolean]("dryrun").defaultValue(true).description("Dry run logittaa haut, joiden tila on muuttunut Mongossa, mutta ei päivitä kantaa.")
    parameter queryParam[Boolean]("force").defaultValue(false).description("Älä laske Mongon datasta oikeaa hashia, vaan migroi joka tapauksessa.")
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/haut", operation(postHakuMigration)) {
    contentType = "text/plain"
    val start = System.currentTimeMillis()
    val dryRun = params("dryrun").toBoolean
    val force = params("force").toBoolean
    val hakuOids = read[Set[String]](request.body)

    val hakuOidsAndHashes: Map[String, String] = if (force) {
      logger.info("force flag given, not calculating real hashes from mongo")
      hakuOids.map((_, "overridden-hash")).toMap
    } else {
      migraatioService.getSijoitteluHashesByHakuOid(hakuOids)
    }

    val hakuOidsWithResults = hakuOidsAndHashes.map(h => (h._1, Try(migraatioService.migrate(h._1, h._2, dryRun))))
    val hakuOidsWithFailures = hakuOidsWithResults.filter(_._2.isFailure)
    if (hakuOidsWithFailures.nonEmpty) {
      val msg = s"${hakuOidsAndHashes.size} haun migraatiosta ${hakuOidsWithFailures.size} epäonnistui, " +
        s"kesti ${System.currentTimeMillis - start} ms. Virheet:"
      logger.error(msg)
      val failureStackTraces = hakuOidsWithFailures.map {
        case (hakuOid, Failure(e)) =>
          logger.error(s"Virhe haun $hakuOid migraatiossa:", e)
          val exceptionOutputWriter = new StringWriter()
          e.printStackTrace(new PrintWriter(exceptionOutputWriter))
          s"Haku $hakuOid : ${exceptionOutputWriter.toString}"
        case x => throw new IllegalStateException(s"Mahdoton tilanne $x . Täällä piti olla vain virheitä.")
      }
      InternalServerError(s"$msg :\n$failureStackTraces")
    } else {
      logger.info(s"postHakuMigration DONE in ${System.currentTimeMillis - start} ms")
      Ok(s"Migraatio onnistui, käytiin läpi ${hakuOidsAndHashes.size} hakuOidia")
    }
  }

  val postHakukohdeMigrationTiming: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Laske hieman lukuja siitä, kauanko sijoittelun tulosten lukeminen sijoitteludb:stä valintarekisteriin migroimista saattaisi kestää"
    // Real body param type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/kellota-hakukohteet", operation(postHakukohdeMigrationTiming)) {
    Ok(migraatioService.getSijoitteluHashesByHakuOid(read[Set[String]](request.body)))
  }

  val getHakuMigrationUi: OperationBuilder = (apiOperation[Unit]("ui")
    summary "Käyttöliittymä hakumigraation ajamiseen tietyille hauille. Avaa URL omaan tabiin, ei toimi swaggerin kautta.")
  get("/ui", operation(getHakuMigrationUi)) {
    redirect("/valinta-tulos-service/sijoitteluntulos-migration.html")
  }
}
