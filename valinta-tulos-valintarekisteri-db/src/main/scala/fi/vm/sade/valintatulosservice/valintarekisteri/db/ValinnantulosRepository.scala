package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.io.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, ValinnantilanTallennus, ValinnantuloksenOhjaus, Valinnantulos}
import slick.dbio.DBIO

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait ValinnantulosRepository extends ValintarekisteriRepository {

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantilaOverridingTimestamp(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None, tilanViimeisinMuutos: Timestamp): DBIO[Unit]

  def updateValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetForValintatapajono(valintatapajonoOid:String): DBIO[List[Valinnantulos]]

  def getLastModifiedForValintatapajono(valintatapajonoOid:String):DBIO[Option[Instant]]

  def getLastModifiedForValintatapajononHakemukset(valintatapajonoOid:String):DBIO[Vector[(String, Instant)]]

  def getHakuForHakukohde(hakukohdeOid:String): String

  def deleteValinnantulos(muokkaaja:String, valinnantulos:Valinnantulos, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def deleteIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid:String, timeout:Duration = Duration(2, TimeUnit.SECONDS)):Option[(Instant, List[Valinnantulos])] =
    runBlockingTransactionally(
      getLastModifiedForValintatapajono(valintatapajonoOid)
        .flatMap {
          case Some(lastModified) => getValinnantuloksetForValintatapajono(valintatapajonoOid).map(vs => Some((lastModified, vs)))
          case None => DBIO.successful(None)
        },
      timeout = timeout
    ) match {
      case Right(result) => result
      case Left(error) => throw error
    }

  def getValinnantuloksetAndLastModifiedDatesForValintatapajono(valintatapajonoOid:String, timeout:Duration = Duration(2, TimeUnit.SECONDS)):Map[String, (Instant, Valinnantulos)] =
    runBlockingTransactionally(getLastModifiedForValintatapajononHakemukset(valintatapajonoOid).zip(getValinnantuloksetForValintatapajono(valintatapajonoOid)), timeout = timeout) match {
      case Right(result) => result._1.map{case (hakemusOid,lastModified) => (hakemusOid, (lastModified, result._2.find(_.hakemusOid == hakemusOid).get))}.toMap
      case Left(error) => throw error
    }
}
