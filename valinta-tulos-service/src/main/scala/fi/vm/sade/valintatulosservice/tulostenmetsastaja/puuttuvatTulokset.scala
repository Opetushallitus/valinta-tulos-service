package fi.vm.sade.valintatulosservice.tulostenmetsastaja

import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import java.util.concurrent.TimeUnit.MINUTES

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.apache.commons.lang3.StringUtils
import slick.dbio.DBIO
import slick.driver.PostgresDriver.api._
import slick.sql.SqlAction

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

case class HaunTiedotListalle(hakuOid: HakuOid, myohaisinKoulutuksenAlkamiskausi: Kausi, hakukohteidenLkm: Int,
                              tarkistettu: Option[ZonedDateTime], haunPuuttuvienMaara: Option[Int])

case class HaunPuuttuvatTulokset[T <: HakukohteenPuuttuvatTuloksetBase](hakuOid: HakuOid, puuttuvatTulokset: Seq[OrganisaationPuuttuvatTulokset[T]])
case class OrganisaationPuuttuvatTulokset[T <: HakukohteenPuuttuvatTuloksetBase](tarjoajaOid: TarjoajaOid, tarjoajanNimi: String, puuttuvatTulokset: Seq[T])
trait HakukohteenPuuttuvatTuloksetBase {
  def hakukohdeOid: HakukohdeOid
  def kohteenNimi: String
  def kohteenValintaUiUrl: URL
}
case class HakukohteenPuuttuvatTulokset(override val hakukohdeOid: HakukohdeOid, override val kohteenNimi: String,
                                        override val kohteenValintaUiUrl: URL, puuttuvatTulokset: Seq[HakutoiveTulosHakemuksella]) extends HakukohteenPuuttuvatTuloksetBase
case class HakukohteenPuuttuvatSummary(override val hakukohdeOid: HakukohdeOid, override val kohteenNimi: String,
                                       override val kohteenValintaUiUrl: URL, puuttuvienMaara: Int) extends HakukohteenPuuttuvatTuloksetBase
case class HakutoiveTulosHakemuksella(hakijaOid: Option[HakijaOid], hakemusOid: HakemusOid, hakukotoiveOid: HakukohdeOid, hakutoiveenNimi: String, tarjoajaOid: TarjoajaOid, tarjoajanNimi: String)
case class HakutoiveTulosRekisterissa(hakemusOid: HakemusOid, hakutoiveOid: HakukohdeOid)

class PuuttuvatTuloksetService(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository, virkailijaBaseUrl: String) extends Logging {
  private val hakukohdeLinkCreator = new SijoittelunTuloksetLinkCreator(virkailijaBaseUrl)
  private val dao = new PuuttuvatTuloksetDao(valintarekisteriDb, hakemusRepository, hakukohdeLinkCreator)

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

  def findSummary(): Seq[HaunTiedotListalle] = {
    valintarekisteriDb.runBlocking(dao.findSummary(), Duration(1, MINUTES))
  }

  def findMissingResultsByOrganisation(hakuOid: HakuOid): Seq[OrganisaationPuuttuvatTulokset[HakukohteenPuuttuvatSummary]] = {
    valintarekisteriDb.runBlocking(dao.findMissingResultsByOrganisation(hakuOid), Duration(1, MINUTES))
  }

  def find(hakuOid: HakuOid): HaunPuuttuvatTulokset[HakukohteenPuuttuvatTulokset] = {
    val eventualOrganisaatioidenTulokset: Future[immutable.Iterable[OrganisaationPuuttuvatTulokset[HakukohteenPuuttuvatTulokset]]] = initialiseMissingRequestsFuture(hakuOid)
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
                val url = hakukohdeLinkCreator.createHakukohdeLink(hakuOid, hakukohdeOid)
                HakukohteenPuuttuvatTulokset(hakukohdeOid, hakukohteenNimi, new URL(url), hakemustenTulokset)
          }
          OrganisaationPuuttuvatTulokset(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvatTulokset.toSeq)
      }
    }
    }
    eventualOrganisaatioidenTulokset
  }
}

class PuuttuvatTuloksetDao(valintarekisteriDb: ValintarekisteriDb, hakemusRepository: HakemusRepository,
                           hakukohdeLinkCreator: SijoittelunTuloksetLinkCreator) extends Logging {
  def save(results: Iterable[OrganisaationPuuttuvatTulokset[HakukohteenPuuttuvatTulokset]], hakuOid: HakuOid): Unit = {
    val saveHakuRow =
      sqlu"""insert into puuttuvat_tulokset_haku (haku_oid, tarkistettu)
             values (${hakuOid.toString}, now())
             on conflict on constraint puuttuvat_tulokset_haku_pk
               do update set tarkistettu = now() where puuttuvat_tulokset_haku.haku_oid = ${hakuOid.toString}"""

    val saveTheRest = results.flatMap { tarjoajaEntry =>
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
        val tarkistettuDateTime = row._4.map(_.toLocalDateTime.atZone(ZoneId.of("Europe/Helsinki")))
        HaunTiedotListalle(HakuOid(row._1), Kausi(row._2), row._3, tarkistettuDateTime, row._5)
      })
  }

  def findMissingResultsByOrganisation(hakuOid: HakuOid): DBIO[Seq[OrganisaationPuuttuvatTulokset[HakukohteenPuuttuvatSummary]]] = {
    val hakuOidString = hakuOid.toString
    val tarjoajaRivit = sql"select tarjoaja_oid, tarjoajan_nimi from puuttuvat_tulokset_tarjoaja where haku_oid = ${hakuOidString}".
      as[(String, String)].map(_.map(r => (TarjoajaOid(r._1), r._2)))
    tarjoajaRivit.map(_.map { case (tarjoajaOid, tarjoajanNimi) =>
      val hakukohteittain = sql"""SELECT hakukohde_oid, hakukohteen_nimi, puuttuvien_maara FROM puuttuvat_tulokset_hakukohde
                WHERE haku_oid = ${hakuOidString} AND tarjoaja_oid = ${tarjoajaOid.toString}""".as[(String, String, Int)].
        map(_.map { case (hakukohdeOidString, kohteenNimi, puuttuvienMaara) =>
          val hakukohdeOid = HakukohdeOid(hakukohdeOidString)
          HakukohteenPuuttuvatSummary(hakukohdeOid, kohteenNimi,
            new URL(hakukohdeLinkCreator.createHakukohdeLink(hakuOid, hakukohdeOid)), puuttuvienMaara)

        })
      hakukohteittain.map(hakukohteidenPuuttuvat => OrganisaationPuuttuvatTulokset(tarjoajaOid, tarjoajanNimi, hakukohteidenPuuttuvat))
    }).flatMap(DBIO.sequence(_))
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

class SijoittelunTuloksetLinkCreator(virkailijaBaseUrl: String) {
  def createHakukohdeLink(hakuOid: HakuOid, hakukohdeOid: HakukohdeOid): String =
    s"https://$virkailijaBaseUrl/valintalaskenta-ui/app/index.html#/haku/$hakuOid/hakukohde/$hakukohdeOid/sijoitteluntulos"
}
