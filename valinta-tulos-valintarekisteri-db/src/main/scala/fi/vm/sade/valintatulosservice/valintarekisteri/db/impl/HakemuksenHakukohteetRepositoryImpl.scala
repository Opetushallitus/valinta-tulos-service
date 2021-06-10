package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemuksenHakukohteet, HakemuksenHakukohteetRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakemusOid
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait HakemuksenHakukohteetRepositoryImpl extends HakemuksenHakukohteetRepository with ValintarekisteriRepository {

  override def findHakemuksenHakukohteet(hakemusOid: HakemusOid): Option[Set[String]] = {
    Option(runBlocking(
      sqlu"""select hakukohde_oids from hakemuksen_hakukohteet where hakemus_oid = ${hakemusOid}"""
    ).toString.split(",").toSet)
  }

  override def storeHakemuksenHakukohteet(hakemuksenHakukohteet: List[HakemuksenHakukohteet]): Unit = {
    hakemuksenHakukohteet.foreach(h => {
      val hakukohteetString = h.hakukohdeOids.map(hk => hk.toString).mkString(",")
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
          case n => DBIO.failed(new RuntimeException(s"Hakemuksen ${h.hakemusOid} hakukohteiden tallennus epäonnistui."))
        })
    })
  }
}
