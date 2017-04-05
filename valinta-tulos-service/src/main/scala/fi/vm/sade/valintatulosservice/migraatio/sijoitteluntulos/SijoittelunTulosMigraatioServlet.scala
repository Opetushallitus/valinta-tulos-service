package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.io.{PrintWriter, StringWriter}

import fi.vm.sade.valintatulosservice.VtsServletBase
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosBatchRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.json4s.jackson.Serialization.read
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{InternalServerError, Ok}

class SijoittelunTulosMigraatioServlet(migraatioService: SijoitteluntulosMigraatioService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("sijoittelun-tulos-migraatio")

  override protected def applicationDescription: String = "REST-API sijoittelun tuloksien migroinniksi valintarekisteriin"

  private val sijoittelunTulosRestClient = new SijoittelunTulosRestClient(appConfig)

  logger.warn("Mountataan Valintarekisterin sijoittelun tuloksien migraatioservlet!")

  val postHakuMigration: OperationBuilder = (apiOperation[String]("migroiHakukohde")
    summary "Migroi sijoitteludb:stä valintarekisteriin hakuja. Toistaiseksi ei välitä siitä, ovatko tiedot muuttuneet"
    parameter queryParam[Boolean]("dryrun").defaultValue(true).description("Dry run logittaa haut, joiden tila on muuttunut Mongossa, mutta ei päivitä kantaa.")
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/haut", operation(postHakuMigration)) {
    val start = System.currentTimeMillis()
    val dryRun = params("dryrun").toBoolean
    val hakuOidsAndHashes = migraatioService.getSijoitteluHashesByHakuOid(read[Set[String]](request.body))

    try {
      hakuOidsAndHashes.foreach(h => migraatioService.migrate(h._1, h._2, dryRun))
      val msg = s"postHakuMigration DONE in ${System.currentTimeMillis - start} ms"
      logger.info(msg)
      Ok(s"Migraatio onnistui, käytiin läpi ${hakuOidsAndHashes.size} hakuOidia")
    } catch {
      case e: Exception =>
        val msg = s"Migraatio epäonnistui, kesti ${System.currentTimeMillis - start} ms"
        logger.error(msg, e)
        val exceptionOutputWriter = new StringWriter()
        e.printStackTrace(new PrintWriter(exceptionOutputWriter))
        InternalServerError(s"$msg : ${e.getMessage} ${exceptionOutputWriter.toString}", reason = e.getMessage)
    }
  }

  val postHakukohdeMigrationTiming: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Laske hieman lukuja siitä, kauanko sijoittelun tulosten lukeminen sijoitteludb:stä valintarekisteriin migroimista saattaisi kestää"
    // Real body param type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/kellota-hakukohteet", operation(postHakukohdeMigrationTiming)) {
    Ok(migraatioService.getSijoitteluHashesByHakuOid(read[Set[String]](request.body)).keys.toSet)
  }

  val getHakuMigrationUi: OperationBuilder = (apiOperation[Unit]("ui")
    summary "Käyttöliittymä hakumigraation ajamiseen tietyille hauille. Avaa URL omaan tabiin, ei toimi swaggerin kautta.")
  get("/ui", operation(getHakuMigrationUi)) {
    redirect("/valinta-tulos-service/sijoitteluntulos-migration.html")
  }
}
