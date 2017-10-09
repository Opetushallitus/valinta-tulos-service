package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL
import java.time.ZoneId
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, Kausi, TarjoajaOid}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._
import slick.sql.SqlAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class PuuttuvatTuloksetDao(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository,
                           hakukohdeLinkCreator: SijoittelunTuloksetLinkCreator) extends Logging {
  def save(results: Iterable[TarjoajanPuuttuvat[HakukohteenPuuttuvat]], hakuOid: HakuOid): Unit = {
    val saveHakuRow =
      sqlu"""insert into puuttuvat_tulokset_haku (haku_oid, tarkistettu)
             values (${hakuOid.toString}, now())
             on conflict on constraint puuttuvat_tulokset_haku_pk
               do update set tarkistettu = now() where puuttuvat_tulokset_haku.haku_oid = ${hakuOid.toString}"""

    val saveTarjoajaAndHakukohdeRows = results.flatMap { tarjoajaEntry =>
      val tarjoajaOid = tarjoajaEntry.tarjoajaOid.toString
      val saveTarjoajaRow: SqlAction[Int, NoStream, Effect] =
        sqlu"""insert into puuttuvat_tulokset_tarjoaja (haku_oid, tarjoaja_oid, tarjoajan_nimi)
               values (${hakuOid.toString}, ${tarjoajaOid}, ${tarjoajaEntry.tarjoajanNimi})
               on conflict on constraint puuttuvat_tulokset_tarjoaja_pk do nothing"""
      val saveHakukohdeRows: Seq[SqlAction[Int, NoStream, Effect]] = tarjoajaEntry.puuttuvatTulokset.map { hakukohdeEntry =>
        val puuttuvienMaara = hakukohdeEntry.puuttuvatTulokset.size
        val hakukohdeOid = hakukohdeEntry.hakukohdeOid.toString
        sqlu"""insert into puuttuvat_tulokset_hakukohde
                              (haku_oid, tarjoaja_oid, hakukohde_oid, hakukohteen_nimi, puuttuvien_maara) values
                              (${hakuOid.toString}, ${tarjoajaOid}, ${hakukohdeOid}, ${hakukohdeEntry.kohteenNimi},
                                ${puuttuvienMaara})
               on conflict on constraint puuttuvat_tulokset_hakukohde_pk
                 do update set puuttuvien_maara = excluded.puuttuvien_maara, hakukohteen_nimi = excluded.hakukohteen_nimi
                   where puuttuvat_tulokset_hakukohde.hakukohde_oid = ${hakukohdeOid}
                     and puuttuvat_tulokset_hakukohde.tarjoaja_oid = ${tarjoajaOid}"""
      }
      saveHakukohdeRows.+:(saveTarjoajaRow)
    }
    val saveResults = valintarekisteriDb.runBlockingTransactionally(DBIO.sequence(saveTarjoajaAndHakukohdeRows.toSeq.+:(saveHakuRow)), Duration(1, MINUTES))
    saveResults match {
      case Right(savedRowCounts) => logger.info(s"Tallennettujen rivien määrät haulle $hakuOid : $savedRowCounts")
      case Left(e) => logger.error(s"Virhe tallennettaessa haun $hakuOid tietoja:", e)
    }
  }

  def findSummary(): DBIO[Seq[HaunTiedotListalle]] = {
    sql"""select distinct hk.haku_oid, max(koulutuksen_alkamiskausi) as myohaisin_koulutuksen_alkamiskausi,
            count(hk.hakukohde_oid) as hakukohteiden_lkm, pth.tarkistettu, sum(pthk.puuttuvien_maara) as haun_puuttuvien_maara
          from hakukohteet hk
            left join puuttuvat_tulokset_haku pth on pth.haku_oid = hk.haku_oid
            left join puuttuvat_tulokset_hakukohde pthk on pthk.haku_oid = pth.haku_oid and pthk.hakukohde_oid = hk.hakukohde_oid
          group by hk.haku_oid, pth.haku_oid, pth.tarkistettu
          order by haun_puuttuvien_maara desc nulls last, myohaisin_koulutuksen_alkamiskausi desc, hk.haku_oid""".
      as[(String, String, Int, Option[java.sql.Timestamp], Option[Int])].
      map(_.map { row =>
        val tarkistettuDateTime = row._4.map(_.toLocalDateTime.atZone(ZoneId.of("Europe/Helsinki")))
        HaunTiedotListalle(HakuOid(row._1), Kausi(row._2), row._3, tarkistettuDateTime, row._5)
      })
  }

  def findMissingResultsByTarjoaja(hakuOid: HakuOid): DBIO[Seq[TarjoajanPuuttuvat[HakukohteenPuuttuvatSummary]]] = {
    val hakuOidString = hakuOid.toString
    val tarjoajaRivit = sql"select tarjoaja_oid, tarjoajan_nimi from puuttuvat_tulokset_tarjoaja where haku_oid = ${hakuOidString}".
      as[(String, String)].map(_.map(r => (TarjoajaOid(r._1), r._2)))
    tarjoajaRivit.map(_.map { case (tarjoajaOid, tarjoajanNimi) =>
      val hakukohteittain = sql"""select hakukohde_oid, hakukohteen_nimi, puuttuvien_maara from puuttuvat_tulokset_hakukohde
                where haku_oid = ${hakuOidString} AND tarjoaja_oid = ${tarjoajaOid.toString}""".as[(String, String, Int)].
        map(_.map { case (hakukohdeOidString, kohteenNimi, puuttuvienMaara) =>
          val hakukohdeOid = HakukohdeOid(hakukohdeOidString)
          HakukohteenPuuttuvatSummary(hakukohdeOid, kohteenNimi,
            new URL(hakukohdeLinkCreator.createHakukohdeLink(hakuOid, hakukohdeOid)), puuttuvienMaara)

        })
      hakukohteittain.map(hakukohteidenPuuttuvat => TarjoajanPuuttuvat(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvat))
    }).flatMap(DBIO.sequence(_))
  }
}
