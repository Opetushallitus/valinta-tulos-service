package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

trait ValinnantulosRepository extends ValintarekisteriRepository {
  def getIlmoittautumisenAikaleimat(henkiloOid: String): DBIO[Iterable[(HakukohdeOid, Instant)]]

  def getIlmoittautumisenAikaleimat(hakuOid: HakuOid, henkiloOids: List[String]): DBIO[Iterable[(String, HakukohdeOid, Instant)]]

  def getIlmoittautumisenAikaleimat(hakuOid: HakuOid): DBIO[Iterable[(String, HakukohdeOid, Instant)]]

  def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def resetIlmoittautuminen(henkiloOid: String, hakukohdeOid: HakukohdeOid): DBIO[Unit]

  def storeValinnantuloksenOhjaus(ohjaus: ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def storeValinnantila(tila: ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def storeValinnantilaOverridingTimestamp(tila: ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None, tilanViimeisinMuutos: TilanViimeisinMuutos): DBIO[Unit]

  def setJulkaistavissa(valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit]

  def setHyvaksyttyJaJulkaistavissa(valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit]

  def setHyvaksyttyJaJulkaistavissa(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid, ilmoittaja: String, selite: String): DBIO[Unit]

  def updateValinnantuloksenOhjaus(ohjaus: ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getMuutoshistoriaForHakemus(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[Muutos]

  def getViimeisinValinnantilaMuutosHyvaksyttyJaJulkaistuJonoOidHistoriasta(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): Option[ValintatapajonoOid]

  def getValinnantuloksetForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Set[Valinnantulos]]

  def getValinnantuloksetForValintatapajono(valintatapajonoOid: ValintatapajonoOid): Set[Valinnantulos]

  def getValinnantuloksetAndLastModifiedDateForValintatapajono(valintatapajonoOid: ValintatapajonoOid, timeout: Duration = Duration(10, TimeUnit.SECONDS)): Option[(Instant, Set[Valinnantulos])]

  def getValinnantuloksetForHaku(hakuOid: HakuOid): DBIO[Set[Valinnantulos]]

  def getValinnantuloksetForHakemus(hakemusOid: HakemusOid): DBIO[Set[Valinnantulos]]

  def getValinnantuloksetForHakemukses(hakemusOids: Set[HakemusOid]): Seq[Valinnantulos]

  def getValinnantuloksetAndLastModifiedDateForHakemus(hakemusOid: HakemusOid): Option[(Instant, Set[Valinnantulos])]

  def getValinnantuloksetForValintatapajonoDBIO(valintatapajonoOid: ValintatapajonoOid): DBIO[Set[Valinnantulos]]

  def getHaunValinnantilat(hakuOid: HakuOid): List[(HakukohdeOid, ValintatapajonoOid, HakemusOid, Valinnantila)]

  def getHakemuksenTilahistoriat(hakemusOid: HakemusOid, valintatapajonoOid: ValintatapajonoOid): List[TilaHistoriaRecord]

  def getHakemustenTilahistoriat(hakemusOids: Set[HakemusOid]): List[TilaHistoriaRecord]

  def getValinnantulostenHakukohdeOiditForHaku(hakuOid: HakuOid): DBIO[List[HakukohdeOid]]

  def getLastModifiedForHakukohde(hakukohdeOid: HakukohdeOid): DBIO[Option[Instant]]

  def getLastModifiedForValintatapajono(valintatapajonoOid: ValintatapajonoOid): DBIO[Option[Instant]]

  def getHakuForHakukohde(hakukohdeOid: HakukohdeOid): HakuOid

  def getHakutoiveidenValinnantuloksetForHakemusDBIO(hakuOid: HakuOid, hakemusOid: HakemusOid): DBIO[List[HakutoiveenValinnantulos]]

  def deleteValinnantulos(muokkaaja: String, valinnantulos: Valinnantulos, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def deleteIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def deleteHyvaksyttyJaJulkaistavissa(henkiloOid: String, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def deleteHyvaksyttyJaJulkaistavissaIfExists(henkiloOid: String, hakukohdeOid: HakukohdeOid, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit]

  def getValinnantuloksetAndLastModifiedDateForHakukohde(hakukohdeOid: HakukohdeOid, timeout: Duration = Duration(10, TimeUnit.SECONDS)): Option[(Instant, Set[Valinnantulos])] =
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

  def getValinnantuloksetAndReadTimeForHaku(hakuOid: HakuOid, timeout:Duration = Duration(5, TimeUnit.MINUTES)):(Instant, Set[Valinnantulos]) = {
    runBlockingTransactionally(
      now().zip(getValinnantuloksetForHaku(hakuOid)), timeout = timeout
    ) match {
      case Right(result) => result
      case Left(error) => throw error
    }
  }

  def getHakijanHyvaksytValinnantilat(hakijaOid: HakijaOid): Set[HyvaksyttyValinnanTila]

  def getHaunJulkaisemattomatHakukohteet(hakuOid: HakuOid): Set[HakukohdeOid]
}
