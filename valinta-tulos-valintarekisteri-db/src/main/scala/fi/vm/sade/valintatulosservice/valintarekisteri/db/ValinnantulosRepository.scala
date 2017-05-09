package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValinnantulosRepository extends ValintarekisteriRepository {
  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def storeValinnantilaOverridingTimestamp(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None, tilanViimeisinMuutos: TilanViimeisinMuutos): DBIO[Unit]
  def setJulkaistavissa(valintatapajonoOid: ValintatapajonoOid): DBIO[Unit]

  def updateValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getMuutoshistoriaForHakemus(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[Muutos]

  def getValinnantuloksetForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Set[Valinnantulos]]
  def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid): DBIO[Set[Valinnantulos]]
  def getValinnantuloksetForHaku(hakuOid: HakuOid): DBIO[Set[Valinnantulos]]
  def getValinnantuloksetForHakemus(hakemusOid: HakemusOid): DBIO[Set[Valinnantulos]]

  def getLastModifiedForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Option[Instant]]
  def getLastModifiedForValintatapajono(valintatapajonoOid: ValintatapajonoOid):DBIO[Option[Instant]]

  def getLastModifiedForValintatapajononHakemukset(valintatapajonoOid: ValintatapajonoOid): DBIO[Set[(HakemusOid, Instant)]]

  def getHakuForHakukohde(hakukohdeOid: HakukohdeOid): HakuOid

  def deleteValinnantulos(muokkaaja:String, valinnantulos:Valinnantulos, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]
  def deleteIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetAndLastModifiedDateForHakukohde(hakukohdeOid: HakukohdeOid, timeout: Duration = Duration(2, TimeUnit.SECONDS)): Option[(Instant, Set[Valinnantulos])] =
    runBlockingTransactionally(
      getLastModifiedForHakukohde(hakukohdeOid)
        .flatMap {
          case Some(lastModified) => getValinnantuloksetForHakukohde(hakukohdeOid).map(vs => Some((lastModified, vs)))
          case None => DBIO.successful(None)
        },
      timeout = timeout
    ) match {
      case Right(result) => result
      case Left(error) => throw error
    }

  def getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid: ValintatapajonoOid, timeout: Duration = Duration(2, TimeUnit.SECONDS)): Option[(Instant, Set[Valinnantulos])] =
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

  def getValinnantuloksetAndLastModifiedDatesForValintatapajono(valintatapajonoOid: ValintatapajonoOid, timeout: Duration = Duration(2, TimeUnit.SECONDS)): Set[(Instant, Valinnantulos)] =
    runBlockingTransactionally(
      getLastModifiedForValintatapajononHakemukset(valintatapajonoOid).zip(getValinnantuloksetForValintatapajono(valintatapajonoOid)),
      timeout = timeout
    ) match {
      case Right((lastModifieds, valinnantulokset)) =>
        val lm = lastModifieds.toMap
        valinnantulokset.map(v => lm(v.hakemusOid) -> v)
      case Left(error) => throw error
    }

  def getValinnantuloksetAndReadTimeForHaku(hakuOid: HakuOid, timeout:Duration = Duration(30, TimeUnit.SECONDS)):(Instant, Set[Valinnantulos]) = {
    runBlockingTransactionally(
      now().zip(getValinnantuloksetForHaku(hakuOid)), timeout = timeout
    ) match {
      case Right(result) => result
      case Left(error) => throw error
    }
  }
}
