package fi.vm.sade.valintatulosservice.tarjonta

import java.util.concurrent.TimeUnit.HOURS

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.DateTime
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, Formats, MappingException}
import scalaj.http.HttpOptions

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal

trait HakuService {
  def getHaku(oid: HakuOid): Either[Throwable, Haku]
  def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, HakukohdeKela]
  def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde]
  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]]
  def getArbitraryPublishedHakukohdeOid(oid: HakuOid): Either[Throwable, HakukohdeOid]
  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]]
}

case class HakuServiceConfig(ophProperties: OphProperties, stubbedExternalDeps: Boolean)

object HakuService {
  def apply(appConfig: AppConfig): HakuService = {
    val config = appConfig.hakuServiceConfig
    if (config.stubbedExternalDeps) {
      HakuFixtures
    } else {
      new CachedHakuService(new TarjontaHakuService(config), appConfig)
    }
  }
}

case class Haku(oid: HakuOid,
                korkeakoulu: Boolean,
                toinenAste: Boolean,
                sallittuKohdejoukkoKelaLinkille: Boolean,
                käyttääSijoittelua: Boolean,
                käyttääHakutoiveidenPriorisointia: Boolean,
                varsinaisenHaunOid: Option[String],
                sisältyvätHaut: Set[String],
                hakuAjat: List[Hakuaika],
                koulutuksenAlkamiskausi: Option[Kausi],
                yhdenPaikanSaanto: YhdenPaikanSaanto,
                nimi: Map[String, String]) {

  val sijoitteluJaPriorisointi = käyttääSijoittelua && käyttääHakutoiveidenPriorisointia
}

case class Hakuaika(hakuaikaId: String, alkuPvm: Option[Long], loppuPvm: Option[Long]) {
  def hasStarted: Boolean = alkuPvm match {
    case Some(alku) => new DateTime().isAfter(new DateTime(alku))
    case _ => true
  }
}

case class Hakukohde(oid: HakukohdeOid,
                     hakuOid: HakuOid,
                     tarjoajaOids: Set[String],
                     koulutusAsteTyyppi: String,
                     hakukohteenNimet: Map[String, String],
                     tarjoajaNimet: Map[String, String],
                     yhdenPaikanSaanto: YhdenPaikanSaanto,
                     tutkintoonJohtava:Boolean,
                     koulutuksenAlkamiskausiUri:String,
                     koulutuksenAlkamisvuosi:Int,
                     organisaatioRyhmaOids: Set[String]) {
  def kkTutkintoonJohtava: Boolean = kkHakukohde && tutkintoonJohtava
  def kkHakukohde: Boolean = koulutusAsteTyyppi == "KORKEAKOULUTUS"

  def koulutuksenAlkamiskausi: Either[Throwable, Kausi] = koulutuksenAlkamiskausiUri match {
    case uri if uri.matches("""kausi_k#\d+""") => Right(Kevat(koulutuksenAlkamisvuosi))
    case uri if uri.matches("""kausi_s#\d+""") => Right(Syksy(koulutuksenAlkamisvuosi))
    case _ => Left(new IllegalStateException(s"Could not deduce koulutuksen alkamiskausi for hakukohde $this"))
  }

  def organisaatioOiditAuktorisointiin: Set[String] = tarjoajaOids ++ organisaatioRyhmaOids
}

case class HakukohdeKela(koulutuksenAlkamiskausi: Option[Kausi],
                         hakukohdeOid: String,
                         tarjoajaOid: String,
                         oppilaitoskoodi: String,
                         koulutuslaajuusarvot: Seq[KoulutusLaajuusarvo])

class HakukohdeKelaSerializer extends CustomSerializer[HakukohdeKela]((formats: Formats) => {
  implicit val f: Formats = formats
  ( {
    case o: JObject =>
      val JString(hakukohdeOid) = o \ "hakukohdeOid"
      val JString(tarjoajaOid) = o \ "tarjoajaOid"
      val JString(oppilaitoskoodi) = o \ "oppilaitosKoodi"
      val JInt(vuosi) = o \ "koulutuksenAlkamisVuosi"

      val kausi: Option[Kausi] = Try(o \ "koulutuksenAlkamiskausiUri" match {
        case JString(kevät) if kevät.contains("kausi_k") => Kevat(vuosi.toInt)
        case JString(syksy) if syksy.contains("kausi_s") => Syksy(vuosi.toInt)
        case x => throw new MappingException(s"Unrecognized kausi URI $x")
      }).toOption
      val children = (o \ "koulutusLaajuusarvos").extractOpt[Seq[KoulutusLaajuusarvo]].getOrElse(Seq())
      HakukohdeKela(
        koulutuksenAlkamiskausi = kausi,
        hakukohdeOid = hakukohdeOid,
        tarjoajaOid = tarjoajaOid,
        oppilaitoskoodi = oppilaitoskoodi,
        koulutuslaajuusarvot = children
      )
  }, { case _ => ??? })
})
protected trait JsonHakuService {
  import org.json4s._
  implicit val formats: Formats = DefaultFormats ++ List(
    new HakukohdeKelaSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer
  )

  protected def toHaku(haku: HakuTarjonnassa): Haku = {
    val korkeakoulu: Boolean = haku.kohdejoukkoUri.startsWith("haunkohdejoukko_12#")
    val amkopeTarkenteet = Set("haunkohdejoukontarkenne_2#", "haunkohdejoukontarkenne_4#", "haunkohdejoukontarkenne_5#", "haunkohdejoukontarkenne_6#")
    val sallittuKohdejoukkoKelaLinkille: Boolean = !haku.kohdejoukonTarkenne.exists(tarkenne => amkopeTarkenteet.exists(tarkenne.startsWith))
    val toinenAste: Boolean = Option(haku.kohdejoukkoUri).exists(k => k.contains("_11") || k.contains("_17") || k.contains("_20"))
    val koulutuksenAlkamisvuosi = haku.koulutuksenAlkamisVuosi
    val kausi = if (haku.koulutuksenAlkamiskausiUri.isDefined && haku.koulutuksenAlkamisVuosi.isDefined) {
      if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_k")) {
            Some(Kevat(koulutuksenAlkamisvuosi.get))
          } else if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_s")) {
            Some(Syksy(koulutuksenAlkamisvuosi.get))
          } else throw new MappingException(s"Haku ${haku.oid} has unrecognized kausi URI '${haku.koulutuksenAlkamiskausiUri.get}' . Full data of haku: $haku")
    } else None

    Haku(
      oid = haku.oid,
      korkeakoulu = korkeakoulu,
      toinenAste = toinenAste,
      sallittuKohdejoukkoKelaLinkille = sallittuKohdejoukkoKelaLinkille,
      käyttääSijoittelua = haku.sijoittelu,
      käyttääHakutoiveidenPriorisointia = haku.usePriority,
      varsinaisenHaunOid = haku.parentHakuOid,
      sisältyvätHaut = haku.sisaltyvatHaut,
      hakuAjat = haku.hakuaikas,
      koulutuksenAlkamiskausi = kausi,
      yhdenPaikanSaanto = haku.yhdenPaikanSaanto,
      nimi = haku.nimi)
  }
}

class CachedHakuService(wrappedService: HakuService, config: AppConfig) extends HakuService {
  private val byOid = TTLOptionalMemoize.memoize[HakuOid, Haku](oid => wrappedService.getHaku(oid), Duration(4, HOURS).toSeconds, config.settings.estimatedMaxActiveHakus)
  private val all = TTLOptionalMemoize.memoize[Unit, List[Haku]](_ => wrappedService.kaikkiJulkaistutHaut, Duration(4, HOURS).toSeconds, 1)

  override def getHaku(oid: HakuOid): Either[Throwable, Haku] = byOid(oid)
  override def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, HakukohdeKela] = wrappedService.getHakukohdeKela(oid)
  override def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] = wrappedService.getHakukohde(oid)
  override def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] = wrappedService.getHakukohdes(oids)
  override def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = wrappedService.getHakukohdeOids(hakuOid)
  override def getArbitraryPublishedHakukohdeOid(oid: HakuOid): Either[Throwable, HakukohdeOid] = wrappedService.getArbitraryPublishedHakukohdeOid(oid)

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = all()
}

private case class HakuTarjonnassa(oid: HakuOid,
                                   hakutapaUri: String,
                                   hakutyyppiUri: String,
                                   kohdejoukkoUri: String,
                                   kohdejoukonTarkenne: Option[String],
                                   koulutuksenAlkamisVuosi: Option[Int],
                                   koulutuksenAlkamiskausiUri: Option[String],
                                   sijoittelu: Boolean,
                                   usePriority: Boolean,
                                   parentHakuOid: Option[String],
                                   sisaltyvatHaut: Set[String],
                                   tila: String,
                                   hakuaikas: List[Hakuaika],
                                   yhdenPaikanSaanto: YhdenPaikanSaanto,
                                   nimi: Map[String, String],
                                   organisaatioOids: Seq[String],
                                   tarjoajaOids: Seq[String]) {
  def julkaistu: Boolean = {
    tila == "JULKAISTU"
  }
}

case class YhdenPaikanSaanto(voimassa: Boolean, syy: String)

class TarjontaHakuService(config: HakuServiceConfig) extends HakuService with JsonHakuService with Logging {

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    val url = config.ophProperties.url("tarjonta-service.haku", oid)
    fetch(url) { response =>
      val hakuTarjonnassa = (parse(response) \ "result").extract[HakuTarjonnassa]
      toHaku(hakuTarjonnassa)
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No haku $oid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing haku $oid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get haku $oid", e)
    }
  }

  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    val url = config.ophProperties.url("tarjonta-service.haku", hakuOid)
    fetch(url) { response =>
      (parse(response) \ "result" \ "hakukohdeOids" ).extract[List[HakukohdeOid]]
    }
  }

  override def getArbitraryPublishedHakukohdeOid(hakuOid: HakuOid): Either[Throwable, HakukohdeOid] = {
    val url = config.ophProperties.url("tarjonta-service.hakukohde.search", Map(
          "tila" -> "VALMIS",
          "tila" -> "JULKAISTU",
          "hakuOid" -> hakuOid,
          "offset" -> 0,
          "limit" -> 1
        ).asJava)
    fetch(url) { response =>
      (parse(response) \ "result" \ "tulokset" \ "tulokset" \ "oid" ).extractOpt[HakukohdeOid]
    }.right.flatMap(_.toRight(new IllegalArgumentException(s"No hakukohde found for haku $hakuOid")))
  }
  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, List[Hakukohde]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }
  def getHakukohdeKela(hakukohdeOid: HakukohdeOid): Either[Throwable, HakukohdeKela] = {
    val hakukohdeUrl = config.ophProperties.url(
      "tarjonta-service.hakukohdekela", hakukohdeOid)
    fetch(hakukohdeUrl) { response =>
      parse(response).extract[HakukohdeKela]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No hakukohde $hakukohdeOid ($hakukohdeUrl) found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing hakukohde $hakukohdeOid ($hakukohdeUrl) failed", e)
      case e: Exception => new RuntimeException(s"Failed to get hakukohde $hakukohdeOid ($hakukohdeUrl)", e)
    }
  }

  def getHakukohde(hakukohdeOid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    val hakukohdeUrl = config.ophProperties.url(
      "tarjonta-service.hakukohde", hakukohdeOid, Map("populateAdditionalKomotoFields" -> true).asJava)
    fetch(hakukohdeUrl) { response =>
      (parse(response) \ "result").extract[Hakukohde]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No hakukohde $hakukohdeOid ($hakukohdeUrl) found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing hakukohde $hakukohdeOid ($hakukohdeUrl) failed", e)
      case e: Exception => new RuntimeException(s"Failed to get hakukohde $hakukohdeOid ($hakukohdeUrl)", e)
    }
  }

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    val url = config.ophProperties.url("tarjonta-service.find", Map("addHakuKohdes" -> false).asJava)
    fetch(url) { response =>
      val haut = (parse(response) \ "result").extract[List[HakuTarjonnassa]]
      haut.filter(_.julkaistu).map(toHaku)
    }
  }

  private def fetch[T](url: String)(parse: String => T): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    )("valinta-tulos-service")
      .responseWithHeaders match {
      case (200, _, resultString) if parseStatus(resultString).contains("NOT_FOUND") =>
        Left(new IllegalArgumentException(s"GET $url failed with status 200: NOT_FOUND"))
      case (404, _, resultString) =>
        Left(new IllegalArgumentException(s"GET $url failed with status 404: $resultString"))
      case (200, _, resultString) =>
        Try(Right(parse(resultString))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of GET $url failed", e))
        }.get
      case (502, _, _) =>
        Left(new RuntimeException(s"GET $url failed with status 502"))
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }
}
