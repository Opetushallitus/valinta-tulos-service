package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.util.Try

trait SijoitteluRepository extends PerformanceLogger { this:Logging =>

  def getLatestSijoitteluajoId(hakuOid:String): Option[Long]

  def getLatestSijoitteluajoId(sijoitteluajoId:String, hakuOid:String): Either[Throwable,Long] = sijoitteluajoId match {
      case x if "latest".equalsIgnoreCase(x) => getLatestSijoitteluajoId(hakuOid).toRight(
        new IllegalArgumentException(s"Yhtään sijoitteluajoa ei löytynyt haulle $hakuOid"))
      case x => Try(x.toLong).toOption.toRight(
        new IllegalArgumentException(s"Väärän tyyppinen sijoitteluajon ID: $sijoitteluajoId"))
  }

  def getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId:String, hakuOid:String):Long =
    getLatestSijoitteluajoId(sijoitteluajoId, hakuOid) match {
      case Right(id) => id
      case Left(failure) => throw failure
    }

  def getSijoitteluajo(sijoitteluajoId:Long): Option[SijoitteluajoRecord]
  def getSijoitteluajonHakukohteet(sijoitteluajoId:Long): List[SijoittelunHakukohdeRecord]
  def getSijoitteluajonHakukohdeOidit(sijoitteluajoId:Long): List[String]
  def getSijoitteluajonValintatapajonot(sijoitteluajoId:Long): List[ValintatapajonoRecord]
  def getSijoitteluajonHakemukset(sijoitteluajoId:Long): List[HakemusRecord]
  def getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId:Long): Map[String,Set[String]]
  def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord]
  def getSijoitteluajonHakijaryhmat(sijoitteluajoId:Long): List[HakijaryhmaRecord]
  def getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId:Long, hakijaryhmaOid:String): List[String]
  def getSijoitteluajonPistetiedot(sijoitteluajoId:Long): List[PistetietoRecord]
  def getSijoitteluajonPistetiedotInChunks(sijoitteluajoId:Long, chunkSize:Int = 200): List[PistetietoRecord]
  def getSijoitteluajonHakemuksetInChunks(sijoitteluajoId:Long, chunkSize:Int = 300): List[HakemusRecord]
  def getValinnantilanKuvaukset(tilankuvausHashes:List[Int]): Map[Int,TilankuvausRecord]

  type HakemusValintatapajono = (String, String)

  def getSijoitteluajonTilahistoriatGroupByHakemusValintatapajono(sijoitteluajoId:Long):Map[HakemusValintatapajono, List[TilaHistoriaRecord]] =
    getSijoitteluajonTilahistoriat(sijoitteluajoId).groupBy(tilahistoria => (tilahistoria.hakemusOid, tilahistoria.valintatapajonoOid))

  def getSijoitteluajonPistetiedotGroupByHakemusValintatapajono(sijoitteluajoId:Long):Map[HakemusValintatapajono, List[PistetietoRecord]] =
    getSijoitteluajonPistetiedot(sijoitteluajoId).groupBy(pistetieto => (pistetieto.hakemusOid, pistetieto.valintatapajonoOid))

  def getValinnantilanKuvauksetForHakemukset(hakemukset:List[HakemusRecord]): Map[Int,TilankuvausRecord] =
    getValinnantilanKuvaukset(hakemukset.map(_.tilankuvausHash).distinct)

  def getSijoitteluajonValintatapajonotGroupedByHakukohde(sijoitteluajoId:Long): Map[String, List[ValintatapajonoRecord]] =
    getSijoitteluajonValintatapajonot(sijoitteluajoId).groupBy(_.hakukohdeOid)

  def getHakemuksenHakija(hakemusOid:String, sijoitteluajoId:Long): Option[HakijaRecord]
  def getHakemuksenHakutoiveet(hakemusOid:String, sijoitteluajoId:Long): List[HakutoiveRecord]
  def getHakemuksenPistetiedot(hakemusOid:String, sijoitteluajoId:Long): List[PistetietoRecord]
  def getHakemuksenHakutoiveidenValintatapajonot(hakemusOid:String, sijoitteluajoId:Long): List[HakutoiveenValintatapajonoRecord]
  def getHakemuksenHakutoiveidenHakijaryhmat(hakemusOid:String, sijoitteluajoId:Long): List[HakutoiveenHakijaryhmaRecord]

  def getSijoitteluajonHakukohde(sijoitteluajoId:Long, hakukohdeOid:String): Option[SijoittelunHakukohdeRecord]
  def getHakukohteenHakijaryhmat(sijoitteluajoId:Long, hakukohdeOid:String): List[HakijaryhmaRecord]
  def getHakukohteenValintatapajonot(sijoitteluajoId:Long, hakukohdeOid:String): List[ValintatapajonoRecord]
  def getHakukohteenPistetiedot(sijoitteluajoId:Long, hakukohdeOid:String): List[PistetietoRecord]
  def getHakukohteenTilahistoriat(sijoitteluajoId:Long, hakukohdeOid:String): List[TilaHistoriaRecord]
  def getHakukohteenHakemukset(sijoitteluajoId:Long, hakukohdeOid:String): List[HakemusRecord]
}
