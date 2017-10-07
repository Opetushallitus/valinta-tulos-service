package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.apache.commons.lang3.StringUtils
import org.json4s.jackson.Serialization
import org.json4s.{Formats, JValue}
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._
import slick.sql.SqlAction

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

case class HaunTiedotListalle(hakuOid: HakuOid, myohaisinKoulutuksenAlkamiskausi: Kausi, hakukohteidenLkm: Int,
                              tarkistettu: Option[Date], haunPuuttuvienMaara: Option[Int])

case class HaunPuuttuvatTulokset(hakuOid: HakuOid, puuttuvatTulokset: Seq[OrganisaationPuuttuvatTulokset])
case class OrganisaationPuuttuvatTulokset(tarjoajaOid: TarjoajaOid, tarjoajanNimi: String, puuttuvatTulokset: Seq[HakukohteenPuuttuvatTulokset])
case class HakukohteenPuuttuvatTulokset(hakukohdeOid: HakukohdeOid, kohteenNimi: String, kohteenValintaUiUrl: URL, puuttuvatTulokset: Seq[HakutoiveTulosHakemuksella]) {
  private implicit val jsonFormats: Formats = JsonFormats.jsonFormats
  def createHakemuksetJson: JValue = Serialization.read("""[{"TODO": "implement"}]""")
}

case class HakutoiveTulosHakemuksella(hakijaOid: Option[HakijaOid], hakemusOid: HakemusOid, hakukotoiveOid: HakukohdeOid, hakutoiveenNimi: String, tarjoajaOid: TarjoajaOid, tarjoajanNimi: String)
case class HakutoiveTulosRekisterissa(hakemusOid: HakemusOid, hakutoiveOid: HakukohdeOid)

class PuuttuvatTuloksetService(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository, virkailijaBaseUrl: String) extends Logging {
  def haeJaTallenna(hakuOid: HakuOid): String = {
    initialiseMissingRequestsFuture(hakuOid).onComplete {
      case Success(results) =>
        val puuttuviaYhteensa = results.flatMap(_.puuttuvatTulokset.map(_.puuttuvatTulokset.size)).sum
        logger.info(s"Aletaan tallentaa haun $hakuOid tuloksia. ${results.size} tarjoajalta $puuttuviaYhteensa puuttuvaa tulosta.")
        Timer.timed(s"Tallennettiin haun $hakuOid puuttuvien tulosten tiedot", 1000) {
          dao.save(results, hakuOid)
        }
      case Failure(e) => logger.error(s"Virhe tallennettaessa haun $hakuOid tuloksia", e)
    }
    s"Initialised searching and storing results for haku $hakuOid"
  }

  private val dao = new PuuttuvatTuloksetDao(valintarekisteriDb, hakemusRepository)
  def findSummary(): Seq[HaunTiedotListalle] = {
    valintarekisteriDb.runBlocking(dao.findSummary(), Duration(1, MINUTES))
  }


  def find(hakuOid: HakuOid): HaunPuuttuvatTulokset = {
    val eventualOrganisaatioidenTulokset: Future[immutable.Iterable[OrganisaationPuuttuvatTulokset]] = initialiseMissingRequestsFuture(hakuOid)
    HaunPuuttuvatTulokset(hakuOid, Await.result(eventualOrganisaatioidenTulokset, Duration(1, MINUTES)).toSeq)
  }


  private def initialiseMissingRequestsFuture(hakuOid: HakuOid) = {
    val puuttuvatToiveetHakemuksilta: Future[Iterator[HakutoiveTulosHakemuksella]] = dao.rekisteristaLoytyvatHakutoiveet(hakuOid).
      zip(dao.hakemuksiltaLoytyvatHakutoiveet(hakuOid)).map { case (toiveetRekisterista, toiveetHakemuksiltaIterator) =>
        toiveetHakemuksiltaIterator.filterNot(t => toiveetRekisterista.contains((t.hakemusOid, t.hakukotoiveOid)))
    }
    val eventualOrganisaatioidenTulokset = puuttuvatToiveetHakemuksilta.map(_.toSeq.groupBy(h => (h.tarjoajaOid, h.tarjoajanNimi))).map { tarjoajittain => {
      tarjoajittain.map {
        case ((tarjoajaOid, tarjoajanNimi), tulokset) =>
          val hakukohteidenPuuttuvatTulokset = tulokset.groupBy(t => (t.hakukotoiveOid, t.hakutoiveenNimi)).map {
              case ((hakukohdeOid, hakukohteenNimi), hakemustenTulokset) =>
                val url = s"https://$virkailijaBaseUrl/valintalaskenta-ui/app/index.html#/haku/$hakuOid/hakukohde/$hakukohdeOid/sijoitteluntulos"
                HakukohteenPuuttuvatTulokset(hakukohdeOid, hakukohteenNimi, new URL(url), hakemustenTulokset)
          }
          OrganisaationPuuttuvatTulokset(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvatTulokset.toSeq)
      }
    }
    }
    eventualOrganisaatioidenTulokset
  }
}

class PuuttuvatTuloksetDao(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository) extends Logging {
  def save(results: Iterable[OrganisaationPuuttuvatTulokset], hakuOid: HakuOid): Unit = {
    val saveHakuRow =
      sqlu"""insert into puuttuvat_tulokset_haku (haku_oid, tarkistettu)
             values (${hakuOid.toString}, now())
             on conflict on constraint puuttuvat_tulokset_haku_pk
               do update set tarkistettu = now() where puuttuvat_tulokset_haku.haku_oid = ${hakuOid.toString}"""

    val saveTheRest = results.flatMap { tarjoajaEntry =>
      val tarjoajaOid = tarjoajaEntry.tarjoajaOid.toString
      val saveTarjoajaRow: SqlAction[Int, NoStream, Effect] =
        sqlu"""insert into puuttuvat_tulokset_tarjoaja (haku_oid, tarjoaja_oid)
               values (${hakuOid.toString}, ${tarjoajaOid})
               on conflict on constraint puuttuvat_tulokset_tarjoaja_pk do nothing"""
      val saveHakukohdeRows: Seq[SqlAction[Int, NoStream, Effect]] = tarjoajaEntry.puuttuvatTulokset.map { hakukohdeEntry =>
        val puuttuvienMaara = hakukohdeEntry.puuttuvatTulokset.size
        // val puuttuvatJson = hakukohdeEntry.createHakemuksetJson
        val hakukohdeOid = hakukohdeEntry.hakukohdeOid.toString
        sqlu"""insert into puuttuvat_tulokset_hakukohde
                              (haku_oid, tarjoaja_oid, hakukohde_oid, puuttuvien_maara) values
                              (${hakuOid.toString}, ${tarjoajaOid}, ${hakukohdeOid},
                                ${puuttuvienMaara})
               on conflict on constraint puuttuvat_tulokset_hakukohde_pk
                 do update set puuttuvien_maara = excluded.puuttuvien_maara
                   where puuttuvat_tulokset_hakukohde.hakukohde_oid = ${hakukohdeOid}
                     and puuttuvat_tulokset_hakukohde.tarjoaja_oid = ${tarjoajaOid}"""
      }
      saveHakukohdeRows.+:(saveTarjoajaRow)
    }
    val saveResults = valintarekisteriDb.runBlockingTransactionally(DBIO.sequence(saveTheRest.toSeq.+:(saveHakuRow)), Duration(1, MINUTES))
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
        HaunTiedotListalle(HakuOid(row._1), Kausi(row._2), row._3, row._4, row._5)
      })
  }

  def hakemuksiltaLoytyvatHakutoiveet(hakuOid: HakuOid): Future[Iterator[HakutoiveTulosHakemuksella]] = {
    logger.info(s"Aletaan hakea hakutoiveita haun $hakuOid hakemuksilta...")
    Future(hakemusRepository.findHakemukset(hakuOid).flatMap {
      logger.info(s"Käsitellään hakutoiveita haun $hakuOid hakemuksilta...")
      h =>
        h.toiveet.map { t =>
          HakutoiveTulosHakemuksella(parseHakemusHenkiloOid(h.henkiloOid), h.oid, t.oid, t.nimi, TarjoajaOid(t.tarjoajaOid), t.tarjoajaNimi)
        }
    })
  }

  def rekisteristaLoytyvatHakutoiveet(hakuOid: HakuOid): Future[Map[(HakemusOid, HakukohdeOid), Seq[HakutoiveTulosRekisterissa]]] = {
    logger.info(s"Aletaan hakea rekisteristä tuloksia haun $hakuOid hakutoiveille...")
    Future(valintarekisteriDb.getHaunValinnantilat(hakuOid)).map(_.map {
      logger.info(s"Käsitellään haun $hakuOid hakutoiveiden tuloksia...")
      valinnantila => HakutoiveTulosRekisterissa(valinnantila._3, valinnantila._1)
    }).map(_.groupBy { tulos => (tulos.hakemusOid, tulos.hakutoiveOid) })
  }

  private def parseHakemusHenkiloOid(oidFromHakemus: String): Option[HakijaOid] = {
    if (StringUtils.isBlank(oidFromHakemus)) {
      None
    } else {
      Some(HakijaOid(oidFromHakemus))
    }
  }
}
