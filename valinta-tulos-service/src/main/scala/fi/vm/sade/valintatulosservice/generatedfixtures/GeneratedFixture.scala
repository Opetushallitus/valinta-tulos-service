package fi.vm.sade.valintatulosservice.generatedfixtures

import com.mongodb.casbah.WriteConcern
import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.testfixtures.MongoMockData
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.hakemus.{HakemusFixture, HakemusFixtures, HakutoiveFixture}
import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritFixtures
import fi.vm.sade.valintatulosservice.sijoittelu.SijoitteluFixtureCreator
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}
import org.mongodb.morphia.AdvancedDatastore

import scala.collection.immutable.Iterable

class GeneratedFixture(haut: List[GeneratedHakuFixture] = List(new GeneratedHakuFixture())) extends Logging {
  def this(haku: GeneratedHakuFixture) = this(List(haku))

  def hakuFixture: HakuOid = HakuFixtures.korkeakouluYhteishaku

  def ohjausparametritFixture = OhjausparametritFixtures.vastaanottoLoppuu2100

  def apply(implicit appConfig: VtsAppConfig) {
    HakuFixtures.useFixture(hakuFixture, haut.map(_.hakuOid))

    val hakemusFixtures = HakemusFixtures()
    logger.info("Clearing...")
    hakemusFixtures.clear
    OhjausparametritFixtures.activeFixture = ohjausparametritFixture
    MongoMockData.clear(appConfig.sijoitteluContext.database)

    logger.info("Iterating...")

    haut.foreach { haku =>
      logger.info("Generating for Haku " + haku.hakuOid)
      logger.info("Valintatulos...")
      val morphia: AdvancedDatastore = appConfig.sijoitteluContext.morphiaDs.asInstanceOf[AdvancedDatastore]
      insertWithProgress(haku.valintatulokset.grouped(100))(valintatulokset => morphia.insert(convert(valintatulokset.toIterable), WriteConcern.Acknowledged))
      logger.info("Sijoittelu...")
      appConfig.sijoitteluContext.sijoitteluDao.persistSijoittelu(haku.sijoittelu)
      logger.info("Hakukohde...")
      insertWithProgress(haku.hakukohteet.grouped(100))(hakukohteet => hakukohteet.foreach(appConfig.sijoitteluContext.hakukohdeDao.persistHakukohde(_, haku.hakuOid.toString)))
      logger.info("Hakemus-kantaan...")
      haku.hakemukset
        .map { hakemus => HakemusFixture(haku.hakuOid, hakemus.hakemusOid, hakemus.hakutoiveet.zipWithIndex.map{ case (hakutoive, index) => HakutoiveFixture(index+1, hakutoive.tarjoajaOid, hakutoive.hakukohdeOid) })}
        .grouped(500).foreach( x => {
          hakemusFixtures.startBulkOperationInsert
          x.foreach(hakemusFixtures.importTemplateFixture(_))
          hakemusFixtures.commitBulkOperationInsert
      })
    }
    logger.info("Done")
  }


  def convert(scalaIterable: scala.collection.Iterable[Valintatulos]): java.lang.Iterable[Valintatulos] = {
    scala.collection.JavaConversions.asJavaIterable(scalaIterable)
  }


  private def insertWithProgress[X](items: Iterator[X])(block: (X => Any)) {
    var checked = System.currentTimeMillis()
    items.zipWithIndex.foreach { case (item, index) =>
      if (index % 10 == 0) {
        val now = System.currentTimeMillis()
        if (now - checked > 1000) {
          print("\r" + index)
          checked = now
        }
      }
      block(item)
    }
  }
}
class GeneratedHakuFixture(val hakuOid: HakuOid = HakuOid("1")) {
  val hakuOidAfterDot = hakuOid.toString.split('.').last
  val sijoitteluajoId: Long = hakuOidAfterDot.substring(0, Math.min(hakuOidAfterDot.length, 5)).toLong

  def hakemukset = List(HakemuksenTulosFixture(HakemusOid("1"), List(
    HakemuksenHakukohdeFixture("1", HakukohdeOid("1"))
  )))

  def kaikkiJonotSijoiteltu: Boolean = true

  lazy val hakukohteet: List[Hakukohde] = {
    val grouped: Map[(String, HakukohdeOid), List[(String, HakukohdeOid, HakemuksenTulosFixture, Int)]] = (for {
      hakemus <- hakemukset
      (hakutoive, index) <- hakemus.hakutoiveet.zipWithIndex
    } yield {
      (hakutoive.tarjoajaOid, hakutoive.hakukohdeOid, hakemus, index + 1)
    }).groupBy { case (tarjoaja: String, hakukohde: HakukohdeOid, hakemus: HakemuksenTulosFixture, hakutoiveNumero: Int) => (tarjoaja, hakukohde)}

    val mapped: Iterable[Hakukohde] = grouped
      .map { case ((tarjoajaId, hakukohdeId), values) => {
      val hakemuksetHakutoiveNumerolla = values.map { case (_, hakukohdeOid, hakemus, hakutoiveNumero) =>
        val jonot: List[ValintatapaJonoFixture] = hakemus.hakutoiveet.find(hakutoive => hakutoive.hakukohdeOid == hakukohdeOid).get.jonot
        (hakemus, jonot, hakutoiveNumero)
      }
      val jonoja = hakemuksetHakutoiveNumerolla.map { case (hakemus, jonot, hakutoiveNumero) => jonot.length}.max
      val jonot = (1 to jonoja).toList.map { jonoNumero =>
        val hakemukset: List[Hakemus] = for {
          (hakemus, jonot, hakutoiveNumero) <- hakemuksetHakutoiveNumerolla
          if (jonot.length >= jonoNumero)
        } yield {
          val hakemuksenJono = jonot(jonoNumero - 1)
          SijoitteluFixtureCreator.newHakemus(hakemus.hakemusOid, hakemus.hakemusOid.toString, hakutoiveNumero, hakemuksenJono.tulos)
        }
        SijoitteluFixtureCreator.newValintatapajono(ValintatapajonoOid(hakukohdeId.toString + "." + jonoNumero), hakemukset)
      }
      SijoitteluFixtureCreator.newHakukohde(hakukohdeId, tarjoajaId, sijoitteluajoId, kaikkiJonotSijoiteltu, jonot)
    }
    }
    mapped.toList
  }

  def julkaistavissa(hakukohde: Hakukohde, hakemus: Hakemus) = {
    (for {
      hakemuksenTulos <- hakemukset
      if (hakemuksenTulos.hakemusOid == HakemusOid(hakemus.getHakemusOid))
      hakutoive <- hakemuksenTulos.hakutoiveet
      if (hakutoive.hakukohdeOid == HakukohdeOid(hakukohde.getOid))
    } yield (hakutoive.julkaistavissa)).headOption.getOrElse(true)
  }

  import scala.collection.JavaConversions._

  lazy val valintatulokset: List[Valintatulos] = for {
    hakukohde <- hakukohteet
    jono: Valintatapajono <- hakukohde.getValintatapajonot
    hakemus: Hakemus <- jono.getHakemukset
  } yield {
    SijoitteluFixtureCreator.newValintatulos(ValintatapajonoOid(jono.getOid), hakuOid, HakemusOid(hakemus.getHakemusOid), HakukohdeOid(hakukohde.getOid), hakemus.getHakijaOid, hakemus.getPrioriteetti, julkaistavissa(hakukohde, hakemus))
  }

  lazy val sijoittelu: Sijoittelu = SijoitteluFixtureCreator.newSijoittelu(hakuOid, sijoitteluajoId, hakukohteet.map(h => HakukohdeOid(h.getOid)))
}

case class HakemuksenTulosFixture(hakemusOid: HakemusOid, hakutoiveet: List[HakemuksenHakukohdeFixture])
case class HakemuksenHakukohdeFixture(tarjoajaOid: String, hakukohdeOid: HakukohdeOid, jonot: List[ValintatapaJonoFixture] = List(ValintatapaJonoFixture(HakemuksenTila.HYVAKSYTTY)), julkaistavissa: Boolean = true)
case class ValintatapaJonoFixture(tulos: HakemuksenTila)
