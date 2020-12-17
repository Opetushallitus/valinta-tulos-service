package fi.vm.sade.valintatulosservice

import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakukohdeRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, HakukohdeRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.json4s.jackson.Serialization._
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scala.collection.parallel.{ForkJoinTaskSupport, ParSet}
import scala.concurrent.Future
import scala.concurrent.forkjoin.ForkJoinPool

case class Status(started: Date)

class HakukohdeRefreshServlet(
  hakukohdeRepository: HakukohdeRepository,
  hakukohdeRecordService: HakukohdeRecordService
)(implicit val swagger: Swagger)
    extends ScalatraServlet
    with Logging
    with JacksonJsonSupport
    with JsonFormats
    with SwaggerSupport
    with UrlGeneratorSupport {

  override protected def applicationDescription: String = "Hakukohdetietojen virkistys API"

  private val running = new AtomicReference[Option[Date]](None)
  import scala.concurrent.ExecutionContext.Implicits.global

  val statusSwagger = (apiOperation[Status]("virkistysStatus")
    summary "Virkistyksen tila"
    tags "virkistys")
  val statusController = get("/", operation(statusSwagger)) {
    running.get() match {
      case Some(started) => Ok(Status(started))
      case None          => NoContent()
    }
  }

  val virkistaSwagger = (apiOperation[Unit]("virkistaHakukohteet")
    summary "Virkistä hakukohteiden tiedot"
    parameter queryParam[Boolean]("dryrun")
      .defaultValue(true)
      .description("Dry run logittaa muuttuneet hakukohteet, mutta ei päivitä kantaa.")
    parameter bodyParam[Set[String]]("hakukohdeOids")
      .description("Virkistettävien hakukohteiden oidit. Huom, tyhjä lista virkistää kaikki!")
    tags "virkistys")
  post("/", operation(virkistaSwagger)) {
    val dryRun = params.getOrElse("dryrun", "true").toBoolean
    val dryRunMsg = if (dryRun) "DRYRUN " else ""
    val started = Some(new Date())
    val hakukohdeOids = read[Set[HakukohdeOid]](request.body)
    if (running.compareAndSet(None, started)) {
      Future {
        try {
          val hakukohteet = findHakukohteet(hakukohdeOids)
          val i = new AtomicInteger(0)
          val pool = new ForkJoinPool(4)
          hakukohteet.tasksupport = new ForkJoinTaskSupport(pool)
          logger.info(s"${dryRunMsg}Refreshing ${hakukohteet.size} hakukohdetta")
          hakukohteet.foreach(hakukohde => {
            val updated = if (dryRun) {
              hakukohdeRecordService.refreshHakukohdeRecordDryRun(hakukohde.oid)
            } else {
              hakukohdeRecordService.refreshHakukohdeRecord(hakukohde.oid)
            }
            if (updated) { i.incrementAndGet() }
          })
          pool.shutdown()
          pool.awaitTermination(30, TimeUnit.SECONDS)
          logger.info(s"${dryRunMsg}Updated $i hakukohdetta")
        } catch {
          case e: Exception => logger.error(s"${dryRunMsg}Refreshing hakukohteet failed", e)
        } finally {
          running.compareAndSet(started, None)
        }
      }
      logger.info(s"${dryRunMsg}Hakukohde refresh started")
    }
    SeeOther(url(statusController))
  }

  private def findHakukohteet(hakukohdeOids: Set[HakukohdeOid]): ParSet[HakukohdeRecord] = {
    if (hakukohdeOids.isEmpty) {
      hakukohdeRepository.all.par
    } else {
      hakukohdeRepository.findHakukohteet(hakukohdeOids).par
    }
  }
}
