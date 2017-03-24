package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import fi.vm.sade.valintatulosservice.VtsServletBase
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.migraatio.valinta.ValintalaskentakoostepalveluService
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import fi.vm.sade.valintatulosservice.tarjonta.TarjontaHakuService
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.json4s.jackson.Serialization.read
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder
import org.scalatra.{InternalServerError, Ok}

class SijoittelunTulosMigraatioServlet(sijoitteluRepository: SijoitteluRepository,
                                       valinnantulosRepository: ValinnantulosRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       tarjontaHakuService: TarjontaHakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("sijoittelun-tulos-migraatio")

  override protected def applicationDescription: String = "REST-API sijoittelun tuloksien migroinniksi valintarekisteriin"

  private val sijoittelunTulosRestClient = new SijoittelunTulosRestClient(appConfig)
  private val migraatioService = new SijoitteluntulosMigraatioService(sijoittelunTulosRestClient, appConfig,
    sijoitteluRepository, valinnantulosRepository, hakukohdeRecordService, tarjontaHakuService, valintalaskentakoostepalveluService)

  logger.warn("Mountataan Valintarekisterin sijoittelun tuloksien migraatioservlet!")

  val postHakuMigration: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Migroi sijoitteludb:stä valintarekisteriin hakuja. Toistaiseksi ei välitä siitä, ovatko tiedot muuttuneet"
    parameter queryParam[Boolean]("dryrun").defaultValue(true).description("Dry run logittaa haut, joiden tila on muuttunut, Mongossa mutta ei päivitä kantaa.")
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/haut", operation(postHakuMigration)) {
    val start = System.currentTimeMillis()
    val dryRun = params("dryrun").toBoolean
    val hakuOids = getHakuoidsWithChangedSijoittelu(read[Set[String]](request.body))

    try {
      hakuOids.foreach {
        migraatioService.migrate(_, dryRun)
      }
    } catch {
      case e: Exception =>
        logger.error("Migraatio epäonnistui", e)
        InternalServerError("Migraatio epäonnistui", reason = e.getMessage)
    }

    val msg = s"postHakuMigration DONE in ${System.currentTimeMillis - start} ms"
    logger.info(msg)
    Ok(-1)
  }

  val postHakukohdeMigrationTiming: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Laske hieman lukuja siitä, kauanko sijoittelun tulosten lukeminen sijoitteludb:stä valintarekisteriin migroimista saattaisi kestää"
    // Real body param type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/kellota-hakukohteet", operation(postHakukohdeMigrationTiming)) {
    Ok(migraatioService.getSijoitteluHashesByHakuOid(read[Set[String]](request.body)).keys.toSet)
  }

  def getHakuoidsWithChangedSijoittelu(hakuOids: Set[String]) = migraatioService.getSijoitteluHashesByHakuOid(hakuOids).keys.toSet
}
