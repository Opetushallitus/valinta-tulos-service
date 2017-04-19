package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Kevat, Syksy}
import org.joda.time.DateTime
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, Formats, JValue, MappingException}

import scala.collection.JavaConversions._
import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

trait HakuService {
  def getHaku(oid: String): Either[Throwable, Haku]
  def getHakukohde(oid: String): Either[Throwable, Hakukohde]
  def getKoulutuses(koulutusOids: Seq[String]): Either[Throwable, Seq[Koulutus]]
  def getKomos(komoOids: Seq[String]): Either[Throwable, Seq[Komo]]
  def getHakukohdes(oids: Seq[String]): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdesForHaku(hakuOid: String): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdeOids(hakuOid:String): Either[Throwable, Seq[String]]
  def getArbitraryPublishedHakukohdeOid(oid: String): Either[Throwable, String]
  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]]
}

case class HakuServiceConfig(ophProperties: OphProperties, stubbedExternalDeps: Boolean)

object HakuService {
  def apply(config: HakuServiceConfig): HakuService = if (config.stubbedExternalDeps) {
    HakuFixtures
  } else {
    new CachedHakuService(new TarjontaHakuService(config))
  }
}

case class Haku(oid: String, korkeakoulu: Boolean, toinenAste: Boolean,
                käyttääSijoittelua: Boolean, varsinaisenHaunOid: Option[String], sisältyvätHaut: Set[String],
                hakuAjat: List[Hakuaika], koulutuksenAlkamiskausi: Option[Kausi], yhdenPaikanSaanto: YhdenPaikanSaanto,
                nimi: Map[String, String])
case class Hakuaika(hakuaikaId: String, alkuPvm: Option[Long], loppuPvm: Option[Long]) {
  def hasStarted = alkuPvm match {
    case Some(alku) => new DateTime().isAfter(new DateTime(alku))
    case _ => true
  }
}

case class Hakukohde(oid: String, hakuOid: String, tarjoajaOids: Set[String], hakukohdeKoulutusOids: List[String],
                     koulutusAsteTyyppi: String, koulutusmoduuliTyyppi: String,
                     hakukohteenNimet: Map[String, String], tarjoajaNimet: Map[String, String], yhdenPaikanSaanto: YhdenPaikanSaanto,
                     tutkintoonJohtava:Boolean, koulutuksenAlkamiskausiUri:String, koulutuksenAlkamisvuosi:Int) {
  def kkTutkintoonJohtava: Boolean = koulutusAsteTyyppi == "KORKEAKOULUTUS" && tutkintoonJohtava

  def koulutuksenAlkamiskausi: Either[Throwable, Kausi] = koulutuksenAlkamiskausiUri match {
    case uri if uri.matches("""kausi_k#\d+""") => Right(Kevat(koulutuksenAlkamisvuosi))
    case uri if uri.matches("""kausi_s#\d+""") => Right(Syksy(koulutuksenAlkamisvuosi))
    case _ => Left(new IllegalStateException(s"Could not deduce koulutuksen alkamiskausi for hakukohde $this"))
  }
}

case class Koodi(uri: String, arvo: String)
case class Komo(oid: String, koulutuskoodi: Option[Koodi], opintojenLaajuusarvo: Option[Koodi])
case class Koulutus(oid: String, koulutuksenAlkamiskausi: Kausi, tila: String, johtaaTutkintoon: Boolean, children: Seq[String],
                    sisaltyvatKoulutuskoodit: Seq[String],
                    koulutuskoodi: Option[Koodi],
                    koulutusaste: Option[Koodi],
                    opintojenLaajuusarvo: Option[Koodi])

class KoulutusSerializer extends CustomSerializer[Koulutus]((formats: Formats) => {
  implicit val f = formats
  ( {
    case o: JObject =>
      val JString(oid) = o \ "oid"
      val JString(tila) = o \ "tila"
      val JInt(vuosi) = o \ "koulutuksenAlkamisvuosi"
      val johtaaTutkintoon = (o \ "johtaaTutkintoon").extractOrElse[Boolean](false)
      val kausi = o \ "koulutuksenAlkamiskausi" \ "uri" match {
        case JString("kausi_k") => Kevat(vuosi.toInt)
        case JString("kausi_s") => Syksy(vuosi.toInt)
        case x => throw new MappingException(s"Unrecognized kausi URI $x")
      }
      val koulutusUriOpt = (o \ "koulutuskoodi" \ "uri").extractOpt[String]
      val koulutusVersioOpt = (o \ "koulutuskoodi" \ "versio").extractOpt[Int]
      val vads: JValue = (o \ "koulutuskoodi")
      def extractKoodi(j: JValue) = Try(Koodi((j \ "uri").extract[String], (j \ "arvo").extract[String])).toOption
      val children = (o \ "children").extractOpt[Seq[String]].getOrElse(Seq())
      val sisaltyvatKoulutuskoodiUris = (o \ "sisaltyvatKoulutuskoodit" \ "uris").extractOpt[Map[String,String]].getOrElse(Map.empty)

      Koulutus(oid, kausi, tila, johtaaTutkintoon,
        children,
        sisaltyvatKoulutuskoodit = sisaltyvatKoulutuskoodiUris.keys.map(k => k.replace("koulutus_", "")).toSeq,
        koulutuskoodi = extractKoodi((o \ "koulutuskoodi")),
          koulutusaste = extractKoodi((o \ "koulutusaste")),
          opintojenLaajuusarvo = extractKoodi((o \ "opintojenLaajuusarvo")))
  }, { case o => ??? })
})
class KomoSerializer extends CustomSerializer[Komo]((formats: Formats) => {
  implicit val f = formats
  ( {
    case o: JObject =>
      val JString(oid) = o \ "oid"
      def extractKoodi(j: JValue) = Try(Koodi((j \ "uri").extract[String], (j \ "arvo").extract[String])).toOption
      Komo(oid,
        koulutuskoodi = extractKoodi((o \ "koulutuskoodi")),
        opintojenLaajuusarvo = extractKoodi((o \ "opintojenLaajuusarvo")))
  }, { case o => ??? })
})
protected trait JsonHakuService {
  import org.json4s._
  implicit val formats = DefaultFormats ++ List(new KoulutusSerializer, new KomoSerializer)

  protected def toHaku(haku: HakuTarjonnassa) = {
    val korkeakoulu: Boolean = haku.kohdejoukkoUri.startsWith("haunkohdejoukko_12#")
    val yhteishaku: Boolean = haku.hakutapaUri.startsWith("hakutapa_01#")
    val varsinainenhaku: Boolean = haku.hakutyyppiUri.startsWith("hakutyyppi_01#1")
    val lisähaku: Boolean = haku.hakutyyppiUri.startsWith("hakutyyppi_03#1")
    val toinenAste: Boolean = Option(haku.kohdejoukkoUri).exists(k => k.contains("_11") || k.contains("_17") || k.contains("_20"))
    val koulutuksenAlkamisvuosi = haku.koulutuksenAlkamisVuosi
    val kausi = if (haku.koulutuksenAlkamiskausiUri.isDefined && haku.koulutuksenAlkamisVuosi.isDefined) {
      if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_k")) {
            Some(Kevat(koulutuksenAlkamisvuosi.get))
          } else if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_s")) {
            Some(Syksy(koulutuksenAlkamisvuosi.get))
          } else throw new MappingException(s"Haku ${haku.oid} has unrecognized kausi URI '${haku.koulutuksenAlkamiskausiUri.get}' . Full data of haku: $haku")
    } else None

    Haku(haku.oid, korkeakoulu, toinenAste, haku.sijoittelu, haku.parentHakuOid,
      haku.sisaltyvatHaut, haku.hakuaikas, kausi, haku.yhdenPaikanSaanto, haku.nimi)
  }
}

class CachedHakuService(wrappedService: HakuService) extends HakuService {
  private val byOid = TTLOptionalMemoize.memoize[String, Haku](oid => wrappedService.getHaku(oid), 4 * 60 * 60)
  private val all = TTLOptionalMemoize.memoize[Unit, List[Haku]](_ => wrappedService.kaikkiJulkaistutHaut, 4 * 60 * 60)

  override def getHaku(oid: String): Either[Throwable, Haku] = byOid(oid)
  override def getHakukohde(oid: String): Either[Throwable, Hakukohde] = wrappedService.getHakukohde(oid)
  override def getKomos(kOids: Seq[String]): Either[Throwable, Seq[Komo]] = wrappedService.getKomos(kOids)
  override def getKoulutuses(koulutusOids: Seq[String]): Either[Throwable, Seq[Koulutus]] = wrappedService.getKoulutuses(koulutusOids)
  override def getHakukohdes(oids: Seq[String]): Either[Throwable, Seq[Hakukohde]] = wrappedService.getHakukohdes(oids)
  override def getHakukohdeOids(hakuOid:String): Either[Throwable, Seq[String]] = wrappedService.getHakukohdeOids(hakuOid)
  override def getHakukohdesForHaku(hakuOid: String): Either[Throwable, Seq[Hakukohde]] = wrappedService.getHakukohdesForHaku(hakuOid)
  override def getArbitraryPublishedHakukohdeOid(oid: String): Either[Throwable, String] = wrappedService.getArbitraryPublishedHakukohdeOid(oid)

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = all()
}

private case class HakuTarjonnassa(oid: String, hakutapaUri: String, hakutyyppiUri: String, kohdejoukkoUri: String,
                                   koulutuksenAlkamisVuosi: Option[Int], koulutuksenAlkamiskausiUri: Option[String],
                                   sijoittelu: Boolean,
                                   parentHakuOid: Option[String], sisaltyvatHaut: Set[String], tila: String,
                                   hakuaikas: List[Hakuaika], yhdenPaikanSaanto: YhdenPaikanSaanto,
                                   nimi: Map[String, String],
                                   organisaatioOids: Seq[String],
                                   tarjoajaOids: Seq[String]) {
  def julkaistu = {
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

  def getHaku(oid: String): Either[Throwable, Haku] = {
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

  def getHakukohdeOids(hakuOid:String): Either[Throwable, Seq[String]] = {
    val url = config.ophProperties.url("tarjonta-service.haku", hakuOid)
    fetch(url) { response =>
      (parse(response) \ "result" \ "hakukohdeOids" ).extract[List[String]]
    }
  }

  override def getArbitraryPublishedHakukohdeOid(hakuOid: String): Either[Throwable, String] = {
    val url = config.ophProperties.url("tarjonta-service.hakukohde.search", mapAsJavaMap(Map(
      "tila" -> "VALMIS",
      "tila" -> "JULKAISTU",
      "hakuOid" -> hakuOid,
      "offset" -> 0,
      "limit" -> 1
    )))
    fetch(url) { response =>
      (parse(response) \ "result" \ "tulokset" \ "tulokset" \ "oid" ).extractOpt[String]
    }.right.flatMap(_.toRight(new IllegalArgumentException(s"No hakukohde found for haku $hakuOid")))
  }
  def getHakukohdes(oids: Seq[String]): Either[Throwable, List[Hakukohde]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }
  def getHakukohde(hakukohdeOid: String): Either[Throwable, Hakukohde] = {
    val hakukohdeUrl = config.ophProperties.url(
      "tarjonta-service.hakukohde", hakukohdeOid, mapAsJavaMap(Map(
        "populateAdditionalKomotoFields" -> true
      )))
    fetch(hakukohdeUrl) { response =>
      (parse(response) \ "result").extract[Hakukohde]
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No hakukohde $hakukohdeOid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing hakukohde $hakukohdeOid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get hakukohde $hakukohdeOid", e)
    }
  }

  def getHakukohdesForHaku(hakuOid: String): Either[Throwable, Seq[Hakukohde]] = {
    getHakukohdeOids(hakuOid).right.flatMap(getHakukohdes)
  }

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    val url = config.ophProperties.url("tarjonta-service.find", mapAsJavaMap(Map(
      "addHakuKohdes" -> false
    )))
    fetch(url) { response =>
      val haut = (parse(response) \ "result").extract[List[HakuTarjonnassa]]
      haut.filter(_.julkaistu).map(toHaku(_))
    }
  }

  def getKoulutuses(koulutusOids: Seq[String]): Either[Throwable, Seq[Koulutus]] = {
    MonadHelper.sequence(koulutusOids.map(getKoulutus))
  }
  def getKomos(komoOids: Seq[String]): Either[Throwable, Seq[Komo]] = {
    MonadHelper.sequence(komoOids.map(getKomo))
  }

  private def getKoulutus(koulutusOid: String): Either[Throwable, Koulutus] = {
    val koulutusUrl = config.ophProperties.url("tarjonta-service.koulutus", koulutusOid)
    fetch(koulutusUrl) { response =>
      (parse(response) \ "result").extract[Koulutus]
    }
  }
  private def getKomo(komoOid: String): Either[Throwable, Komo] = {
    val komoUrl = config.ophProperties.url("tarjonta-service.komo", komoOid)
    fetch(komoUrl) { response =>
      (parse(response) \ "result").extract[Komo]
    }
  }
  private def fetch[T](url: String)(parse: (String => T)): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    ).header("clientSubSystemCode", "valinta-tulos-service")
      .header("Caller-id", "valinta-tulos-service")
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
