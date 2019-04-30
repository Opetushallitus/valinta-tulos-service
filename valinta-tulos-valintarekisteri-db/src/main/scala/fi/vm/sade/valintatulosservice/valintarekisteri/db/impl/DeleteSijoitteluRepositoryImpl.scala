package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.concurrent.TimeUnit

import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.DeleteSijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait DeleteSijoitteluRepositoryImpl extends DeleteSijoitteluRepository with ValintarekisteriRepository {

  override def findSijoitteluAjotSkippingFirst(hakuOid: HakuOid, offset: Int): Seq[Long] = timed(s"Haetaan haun $hakuOid vanhojen sijoittelujen id:t", 100) {
    runBlocking(
      sql"""select id from sijoitteluajot where haku_oid = ${hakuOid} and poistonesto = false order by start desc offset $offset""".as[Long])
  }

  override def listHakuAndSijoitteluAjoCount(): Seq[(HakuOid, Int)] = timed("Lasketaan hakujen poistettavissa olevien sijoitteluajojen määrä", 100) {
    runBlocking(
      sql"""select haku_oid, count(*) from sijoitteluajot where poistonesto = false group by haku_oid order by count desc""".as[(HakuOid,Int)])
  }

  override def deleteSijoitteluajot(hakuOid: HakuOid, sijoitteluajoIds: Seq[Long]): Unit = sijoitteluajoIds.sorted.foreach(deleteSijoitteluajo(hakuOid, _))

  override def deleteSijoitteluajo(hakuOid: HakuOid, sijoitteluajoId: Long): Unit = timed(s"Delete haun $hakuOid sijoitteluajo $sijoitteluajoId", 100) {
    val deleteOperationsWithDescriptions: Seq[(String, DBIO[Any])] = Seq(
      ("delete hakijaryhman_hakemukset", sqlu"delete from hakijaryhman_hakemukset where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete hakijaryhmat", sqlu"delete from hakijaryhmat where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete pistetiedot", sqlu"delete from pistetiedot where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete jonosijat", sqlu"delete from jonosijat where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete sivssnov_sijoittelun_varasijatayton_rajoitus", sqlu"delete from sivssnov_sijoittelun_varasijatayton_rajoitus where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete valintatapajonot", sqlu"delete from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete sijoitteluajon_hakukohteet", sqlu"delete from sijoitteluajon_hakukohteet where sijoitteluajo_id = ${sijoitteluajoId}"),
      ("delete sijoitteluajo", sqlu"delete from sijoitteluajot where id = ${sijoitteluajoId}")
    )

    val (descriptions, sqls) = deleteOperationsWithDescriptions.unzip

    val checkNoPoistonestoAndNotLatest =
      sql"""select id from sijoitteluajot where id = ${sijoitteluajoId} and poistonesto = false and id not in (
            select max(id) from sijoitteluajot group by haku_oid)""".as[Long]

    logger.info(s"Deleting haun $hakuOid sijoitteluajo $sijoitteluajoId")
    runBlockingTransactionally(
      checkNoPoistonestoAndNotLatest.headOption.flatMap {
        case Some(id) => DBIO.successful(id)
        case None => DBIO.failed(new IllegalArgumentException(s"Sijoitteluajoa $sijoitteluajoId ei voida poistaa!"))
      }.andThen(
        DBIO.sequence(sqls)
      ), timeout = Duration(30, TimeUnit.MINUTES)) match {

      case Right(rowCounts) =>
        logger.info(s"Delete of sijoitteluajo $sijoitteluajoId of haku $hakuOid successful. " +
          s"Lines affected:\n\t${descriptions.zip(rowCounts).mkString("\n\t")}")
      case Left(t) =>
        logger.error(s"Could not delete sijoitteluajo $sijoitteluajoId of haku $hakuOid", t)
        throw t
    }
  }

  override def acquireLockForSijoitteluajoCleaning(lockId: Int): Seq[Boolean] = {
    runBlocking(
      sql"""select pg_try_advisory_lock($lockId)""".as[Boolean]
    )
  }

  override def clearLockForSijoitteluajoCleaning(lockId: Int): Seq[Boolean] = {
    runBlocking(
      sql"""select pg_advisory_unlock($lockId)""".as[Boolean]
    )
  }
}
