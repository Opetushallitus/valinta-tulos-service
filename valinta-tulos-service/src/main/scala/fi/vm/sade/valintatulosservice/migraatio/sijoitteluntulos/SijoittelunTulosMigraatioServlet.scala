package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

import com.mongodb.{BasicDBObjectBuilder, DBCursor}
import fi.vm.sade.utils.Timer
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

/**
  * Work in progress.
  */
class SijoittelunTulosMigraatioServlet(sijoitteluRepository: SijoitteluRepository,
                                       valinnantulosRepository: ValinnantulosRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       tarjontaHakuService: TarjontaHakuService,
                                       valintalaskentakoostepalveluService: ValintalaskentakoostepalveluService)(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("sijoittelun-tulos-migraatio")

  override protected def applicationDescription: String = "REST-API sijoittelun tuloksien migroinniksi valintarekisteriin"

  private val digester = MessageDigest.getInstance("MD5")
  private val adapter = new HexBinaryAdapter()

  private val sijoittelunTulosRestClient = new SijoittelunTulosRestClient(appConfig)
  private val mongoClient = new SijoitteluntulosMigraatioService(sijoittelunTulosRestClient, appConfig,
    sijoitteluRepository, valinnantulosRepository, hakukohdeRecordService, tarjontaHakuService, valintalaskentakoostepalveluService)

  logger.warn("Mountataan Valintarekisterin sijoittelun tuloksien migraatioservlet!")

  val postHakuMigration: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Migroi sijoitteludb:stä valintarekisteriin hakuja. Toistaiseksi ei välitä siitä, ovatko tiedot muuttuneet"
    parameter queryParam[Boolean]("dryrun").defaultValue(true).description("Dry run logittaa haut, joiden tila on muuttunut, Mongossa mutta ei päivitä kantaa.")
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/haut", operation(postHakuMigration)) {
    val start = System.currentTimeMillis()
    val dryRun = params("dryrun").toBoolean
    val hakuOids = read[Set[String]](request.body)

    try {
      hakuOids.foreach {
        mongoClient.migrate(_, dryRun)
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
    val start = System.currentTimeMillis()
    val hakuOids = read[Set[String]](request.body)
    var hakuOidsSijoitteluHashes: Map[String, String] = Map()

    hakuOids.par.foreach { hakuOid =>
      Timer.timed(s"Processing haku $hakuOid", 0) {
        sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).map(_.getSijoitteluajoId) match {
          case Some(sijoitteluajoId) =>
            logger.info(s"Latest sijoitteluajoId from haku $hakuOid is $sijoitteluajoId")
            val newHash: String = getSijoitteluHash(sijoitteluajoId, hakuOid)
            sijoitteluRepository.getSijoitteluHash(hakuOid, newHash) match {
              case Some(_) =>
                logger.info(s"Haku $hakuOid hash is up to date, skipping saving its sijoittelu.")
              case _ =>
                hakuOidsSijoitteluHashes += (hakuOid -> newHash)
                logger.info(s"Hash for haku $hakuOid didn't exist yet or has changed, saving sijoittelu.")
                sijoitteluRepository.saveSijoittelunHash(hakuOid, newHash)
            }

          case _ => logger.info(s"No sijoittelus for haku $hakuOid")
        }
      }
      logger.info("=================================================================")
    }
    val msg = s"DONE in ${System.currentTimeMillis - start} ms"
    logger.info(msg)
    logger.info(hakuOidsSijoitteluHashes.toString())
    Ok(hakuOidsSijoitteluHashes)
  }

  private def getSijoitteluHash(sijoitteluajoId: Long, hakuOid: String): String = {
    val query = new BasicDBObjectBuilder().add("sijoitteluajoId", sijoitteluajoId).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Hakukohde").find(query)

    if (!cursor.hasNext) logger.info(s"No hakukohdes for haku $hakuOid")

    val hakukohteetHash = getCursorHash(cursor)
    val valintatuloksetHash = getValintatuloksetHash(hakuOid)
    adapter.marshal(digestString(hakukohteetHash.concat(valintatuloksetHash)))
  }

  private def getValintatuloksetHash(hakuOid: String): String = {
    val query = new BasicDBObjectBuilder().add("hakuOid", hakuOid).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Valintatulos").find(query)

    if (!cursor.hasNext) logger.info(s"No valintatulos' for haku $hakuOid")

    getCursorHash(cursor)
  }

  private def getCursorHash(cursor: DBCursor): String = {
    var res: String = ""
    try {
      while (cursor.hasNext) {
        val nextString = cursor.next().toString
        val stringBytes = digestString(nextString)
        val hex = adapter.marshal(stringBytes)
        res = res.concat(hex)
      }
    } catch {
      case e: Exception => logger.error(e.getMessage)
    } finally {
      cursor.close()
    }
    res
  }

  private def digestString(hakukohdeString: String): Array[Byte] = digester.digest(hakukohdeString.getBytes("UTF-8"))
}
