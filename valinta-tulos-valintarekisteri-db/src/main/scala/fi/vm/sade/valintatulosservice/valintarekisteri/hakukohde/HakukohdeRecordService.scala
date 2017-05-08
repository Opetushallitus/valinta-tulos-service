package fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakukohdeRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, HakukohdeRecord, Kausi, Kevat, Syksy}

import scala.util.{Failure, Success, Try}

class HakukohdeRecordService(hakuService: HakuService, hakukohdeRepository: HakukohdeRepository, parseLeniently: Boolean) extends Logging {

  def getHaunHakukohdeRecords(oid: HakuOid): Either[Throwable, Seq[HakukohdeRecord]] = {
    hakuService.getHakukohdeOids(oid).right.flatMap(getHakukohdeRecords)
  }

  def getHakukohteidenKoulutuksenAlkamiskausi(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[(HakukohdeOid, Option[Kausi])]] = {
    getHakukohdeRecords(oids).right.map(_.map(hakukohde =>
      (hakukohde.oid, if (hakukohde.yhdenPaikanSaantoVoimassa) Some(hakukohde.koulutuksenAlkamiskausi) else None)))
  }

  def getHaunKoulutuksenAlkamiskausi(oid: HakuOid): Either[Throwable, Kausi] = {
    Try(hakukohdeRepository.findHaunArbitraryHakukohde(oid)) match {
      case Success(Some(hakukohde)) => Right(hakukohde.koulutuksenAlkamiskausi)
      case Success(None) => hakuService.getArbitraryPublishedHakukohdeOid(oid)
        .right.flatMap(fetchAndStoreHakukohdeDetails).right.map(_.koulutuksenAlkamiskausi)
      case Failure(e) => Left(e)
    }
  }

  def getHakukohdeRecords(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[HakukohdeRecord]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohdeRecord(oid))
  }

  def getHakukohdeRecord(oid: HakukohdeOid): Either[Throwable, HakukohdeRecord] = {
    // hakukohdeRecord is cached in DB to enable vastaanotto queries
    Try(hakukohdeRepository.findHakukohde(oid)) match {
      case Success(Some(hakukohde)) => Right(hakukohde)
      case Success(None) => fetchAndStoreHakukohdeDetails(oid)
      case Failure(e) => Left(e)
    }
  }

  def refreshHakukohdeRecord(oid: HakukohdeOid): Boolean = {
    refreshHakukohdeRecord(oid, (_, fresh) => hakukohdeRepository.updateHakukohde(fresh))
  }

  def refreshHakukohdeRecordDryRun(oid: HakukohdeOid): Boolean = {
    refreshHakukohdeRecord(oid, _ != _)
  }

  private def refreshHakukohdeRecord(oid: HakukohdeOid, update: (HakukohdeRecord, HakukohdeRecord) => Boolean): Boolean = {
    val old = hakukohdeRepository.findHakukohde(oid).get
    val vastaanottoja = hakukohdeRepository.hakukohteessaVastaanottoja(oid)
    fetchHakukohdeDetails(oid) match {
      case Right(fresh) =>
        if (update(old, fresh)) {
          if (vastaanottoja) {
            logger.warn(s"Updated hakukohde from $old to $fresh. Hakukohde had vastaanottos.")
          } else {
            logger.info(s"Updated hakukohde from $old to $fresh.")
          }
          true
        } else {
          false
        }
      case Left(t) if vastaanottoja =>
        logger.error(s"Updating hakukohde $oid failed. Hakukohde had vastaanottos.", t)
        false
      case Left(t) =>
        logger.warn(s"Updating hakukohde $oid failed.", t)
        false
    }
  }

  private def fetchAndStoreHakukohdeDetails(oid: HakukohdeOid): Either[Throwable, HakukohdeRecord] = {
    val fresh = fetchHakukohdeDetails(oid)
    fresh.left.foreach(t => logger.warn(s"Error fetching hakukohde ${oid} details. Cannot store it to the database.", t))
    fresh.right.foreach(hakukohdeRepository.storeHakukohde)
    fresh
  }

  private def fetchHakukohdeDetails(oid: HakukohdeOid): Either[Throwable, HakukohdeRecord] = {
    for {
      hakukohde <- hakuService.getHakukohde(oid).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      alkamiskausi <- resolveKoulutuksenAlkamiskausi(hakukohde, haku).right
    } yield HakukohdeRecord(hakukohde.oid, haku.oid, hakukohde.yhdenPaikanSaanto.voimassa, hakukohde.kkTutkintoonJohtava, alkamiskausi)
  }

  private def resolveKoulutuksenAlkamiskausi(hakukohde: Hakukohde, haku: Haku): Either[Throwable, Kausi] = {
    hakukohde.koulutuksenAlkamiskausi.left.flatMap(t => {
      if (parseLeniently) {
        logger.warn(s"No alkamiskausi for hakukohde ${hakukohde.oid}. Falling back to koulutuksen alkamiskausi from haku: ${haku.koulutuksenAlkamiskausi}")
        haku.koulutuksenAlkamiskausi.toRight(new IllegalStateException(s"No koulutuksen alkamiskausi on haku $haku"))
      } else {
        Left(t)
      }
    })
  }
}
