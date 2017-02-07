package fi.vm.sade.valintatulosservice.migraatio.sijoitteluntulos

import java.security.MessageDigest
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.BasicDBObjectBuilder
import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.VtsServletBase
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.sijoittelu.SijoittelunTulosRestClient
import org.json4s.jackson.Serialization.read
import org.scalatra.Ok
import org.scalatra.swagger.Swagger
import org.scalatra.swagger.SwaggerSupportSyntax.OperationBuilder

/**
  * Work in progress. This code does not do anything very meaningful yet, just
  * takes some performance statistics for same operations bound to be used in the migration.
  */
class SijoittelunTulosMigraatioServlet()(implicit val swagger: Swagger, appConfig: VtsAppConfig) extends VtsServletBase {
  override val applicationName = Some("sijoittelun-tulos-migraatio")

  override protected def applicationDescription: String = "REST-API sijoittelun tuloksien migroinniksi valintarekisteriin"

  private val objectMapper = new ObjectMapper()
  private val digester = MessageDigest.getInstance("MD5")
  private val adapter = new HexBinaryAdapter()

  private val sijoittelunTulosRestClient = new SijoittelunTulosRestClient(appConfig)

  logger.warn("Mountataan Valintarekisterin sijoittelun tuloksien migraatioservlet!")

  val postHakukohdeMigration: OperationBuilder = (apiOperation[Int]("migroiHakukohde")
    summary "Migroi sijoitteludb:stä valintarekisteriin hakukohteita, jos niiden tiedot ovat muuttuneet"
    // Real body param type cannot be used because of unsupported scala enumerations: https://github.com/scalatra/scalatra/issues/343
    parameter queryParam[Boolean]("dryrun").defaultValue(true).description("Dry run logittaa hakukohteet, joiden tila on muuttunut, Mongossa mutta ei päivitä kantaa.")
    parameter bodyParam[Set[String]]("hakuOids").description("Virkistettävien hakujen oidit. Huom, tyhjä lista virkistää kaikki!"))
  post("/hakukohteet", operation(postHakukohdeMigration)) {
    val start = System.currentTimeMillis()
    val dryRun = params("dryrun").toBoolean
    val hakuOids = read[Set[String]](request.body)

    System.out.println("EXCELIIN\t$sijoitteluAjoId\t$cursorHasNextTotal\t$cursorNextTotal\t$toStringTotal\t$digestTotal\t$marshalTotal\t$printTotal")

    hakuOids.foreach { hakuOid =>
      logger.info(s"Processing haku $hakuOid")
      Timer.timed(s"Processing haku $hakuOid", 0) {
        sijoittelunTulosRestClient.fetchLatestSijoitteluAjoFromSijoitteluService(hakuOid, None).map(_.getSijoitteluajoId).foreach { sijoitteluAjoId =>
          logger.info(s"Latest sijoitteluAjoId from haku $hakuOid is $sijoitteluAjoId")
          findWithCursorLoop(sijoitteluAjoId)
        }
      }
      logger.info("=================================================================\n")
    }
    val msg = s"DONE in ${System.currentTimeMillis - start} ms"
    logger.info(msg)
    System.err.println(msg)
    Ok(-1)
  }

  private def findWithCursorLoop(sijoitteluAjoId: Long) = {
    val start = System.currentTimeMillis()

    var cursorHasNextTotal: Long = 0
    var cursorNextTotal: Long = 0
    var toStringTotal: Long = 0
    var digestTotal: Long = 0
    var marshalTotal: Long = 0
    var printTotal: Long = 0

    var nOfHakukohde = 0

    val query = new BasicDBObjectBuilder().add("sijoitteluajoId", sijoitteluAjoId).get()
    val cursor = appConfig.sijoitteluContext.morphiaDs.getDB.getCollection("Hakukohde").find(query)
    try {
      val (c2, cursorNext) = Timer2.timed() { cursor.hasNext }
      cursorHasNextTotal = cursorHasNextTotal + cursorNext
      var continuing: Boolean = c2

      while (continuing) {
        val (o, cursorNext) = Timer2.timed() { cursor.next() }
        cursorNextTotal = cursorNextTotal + cursorNext

        val (s, toString) = Timer2.timed() { o.toString }
        toStringTotal = toStringTotal + toString

        val (d, digest) = Timer2.timed() { digester.digest(s.getBytes("UTF-8")) }
        digestTotal = digestTotal + digest

        val (hex, marshal) = Timer2.timed() { adapter.marshal(d) }
        marshalTotal = marshalTotal + marshal

        val (c, cursorHasNext) = Timer2.timed() { cursor.hasNext }
        cursorHasNextTotal = cursorHasNextTotal + cursorHasNext
        continuing = c
        nOfHakukohde = nOfHakukohde + 1

        val (x, print) = Timer2.timed() { System.out.println(hex) }
        printTotal = printTotal + print
      }
    } finally {
      cursor.close()
    }
    System.out.println(s"EXCELIIN\t$sijoitteluAjoId\t$cursorHasNextTotal\t$cursorNextTotal\t$toStringTotal\t$digestTotal\t$marshalTotal\t$printTotal")
    System.err.println(s"cursor.next business for sijoitteluajo $sijoitteluAjoId took: ${System.currentTimeMillis() - start} ms for $nOfHakukohde hakukohteet")
  }
}

object Timer2 extends Logging {
  def timed[R](blockname: String = "", thresholdMs: Int = 0)(block: => R): (R, Long) = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    val time: Long = (t1 - t0) / 1000000
    (result, time)
  }
}
