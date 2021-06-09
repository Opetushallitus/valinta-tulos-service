package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemuksenHakukohteet, HakemuksenHakukohteetRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait HakemuksenHakukohteetRepositoryImpl extends HakemuksenHakukohteetRepository with ValintarekisteriRepository {

  override def findHakemuksenHakukohde(oid: HakemusOid): Option[HakemuksenHakukohteet] = ???

  override def findHakemuksenHakukohteet(hakemusOids: Set[HakemusOid]): Set[HakemuksenHakukohteet] = ???

  override def storeHakemuksenHakukohteet(hakemuksenHakukohteet: List[HakemuksenHakukohteet]): Unit = {
    hakemuksenHakukohteet.foreach(h => {
      val hakukohteetString = "[" + h.hakukohdeOids.map(hk => hk.toString).mkString(",") + "]"
      runBlocking(
        sqlu"""insert into hakemuksen_hakukohteet (
               hakemus_oid,
               hakukohde_oids
           ) values (
               ${h.hakemusOid},
               ${hakukohteetString}
           ) on conflict on constraint hakemuksen_hakukohteet_pkey
           do update set
               hakukohde_oids = ${hakukohteetString}
           where hakemuksen_hakukohteet.hakemus_oid = ${h.hakemusOid}
      """.flatMap {
          case 1 => DBIO.successful(true)
          case 0 => DBIO.successful(false)
          case n => DBIO.failed(new RuntimeException(s"vituix män!"))
          //        case n => DBIO.failed(new RuntimeException(s"Odottamaton päivitysten määrä $n tallennettaessa tilat_kuvaukset riviä hash-koodilla $valinnanTilanKuvausHashCode hakukohteelle $hakukohdeOid, valintatapajonolle $valintatapajonoOid ja hakemukselle $hakemusOid"))
        })
    })
  }
}
