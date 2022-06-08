package fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakukohdeRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiKktutkintoonJohtavaHakukohde, EiYPSHakukohde, HakuOid, HakukohdeOid, HakukohdeRecord, Kausi, YPSHakukohde}

import scala.util.{Failure, Success, Try}

class HakukohdeRecordService(hakuService: HakuService, hakukohdeRepository: HakukohdeRepository, parseLeniently: Boolean) extends Logging {

  def getHakukohdeRecords(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[HakukohdeRecord]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohdeRecord(oid))
  }

  def getHakukohdeRecords(hakuOid: HakuOid, oids: Set[HakukohdeOid]): Either[Throwable, List[HakukohdeRecord]] = {
    logger.info(s"getHakukohdeRecords for haku $hakuOid")
    for {
      hakukohteet <- (Try(hakukohdeRepository.findHaunHakukohteet(hakuOid).filter(h => oids.contains(h.oid))) match {
        case Success(hs) => Right(hs)
        case Failure(t) => Left(t)
      }).right
      missingHakukohteet <- MonadHelper.sequence(for { oid <- oids.diff(hakukohteet.map(_.oid)) } yield fetchAndStoreHakukohdeDetails(oid, Some(hakuOid))).right
    } yield (hakukohteet ++ missingHakukohteet).toList
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

  private def fetchAndStoreHakukohdeDetails(oid: HakukohdeOid, hakuOid: Option[HakuOid] = None): Either[Throwable, HakukohdeRecord] = {
    logger.info(s"fetchAndStoreHakukohdeDetails for $oid in haku $hakuOid")
    val fresh = fetchHakukohdeDetails(oid)
    fresh.left.foreach(t => logger.error(s"Error fetching hakukohde ${oid} details. Cannot store it to the database.", t))
    fresh.right.foreach(hakukohdeRepository.storeHakukohde)
    logger.info(s"done: fetchAndStoreHakukohdeDetails for $oid in haku $hakuOid")
    fresh
  }

  private def fetchHakukohdeDetails(oid: HakukohdeOid): Either[Throwable, HakukohdeRecord] = {
    for {
      hakukohde <- hakuService.getHakukohde(oid).right
      haku <- hakuService.getHaku(hakukohde.hakuOid).right
      hakukohdeRecord <- ((hakukohde.yhdenPaikanSaanto.voimassa, hakukohde.kkTutkintoonJohtava, resolveKoulutuksenAlkamiskausi(hakukohde, haku)) match {
        case (true, true, Some(kausi)) => Right(YPSHakukohde(oid, haku.oid, kausi))
        case (false, true, Some(kausi)) => Right(EiYPSHakukohde(oid, haku.oid, kausi))
        case (false, false, kausi) => Right(EiKktutkintoonJohtavaHakukohde(oid, haku.oid, kausi))
        case (true, false, _) => Left(new IllegalStateException(s"Haun ${haku.oid} YPS hakukohde $oid ei ole kktutkintoon johtava"))
        case (true, _, None) => Left(new IllegalStateException(s"Haun ${haku.oid} YPS hakukohteella $oid ei ole koulutuksen alkamiskautta"))
        case (_, true, None) => Left(new IllegalStateException(s"Kktutkintoon johtavalla haun ${haku.oid} hakukohteella $oid ei ole koulutuksen alkamiskautta"))
      }).right
    } yield hakukohdeRecord
  }

  private def resolveKoulutuksenAlkamiskausi(hakukohde: Hakukohde, haku: Haku): Option[Kausi] = {
    (hakukohde.koulutuksenAlkamiskausi, parseLeniently) match {
      case (None, true) =>
        logger.warn(s"No alkamiskausi for hakukohde ${hakukohde.oid}. Falling back to koulutuksen alkamiskausi from haku: ${haku.koulutuksenAlkamiskausi}")
        haku.koulutuksenAlkamiskausi
      case (alkamiskausi, _) =>
        alkamiskausi
    }
  }
}
