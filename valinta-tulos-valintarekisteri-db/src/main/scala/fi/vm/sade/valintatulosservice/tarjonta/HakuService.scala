package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.{AppConfig, StubbedExternalDeps, ValintarekisteriOphUrlProperties}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Kevat, Syksy}
import org.joda.time.DateTime
import org.json4s.JsonAST.{JBool, JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, Formats, MappingException}

import scala.util.Try
import scala.util.control.NonFatal
import scalaj.http.HttpOptions

trait HakuService {
  def getHaku(oid: String): Either[Throwable, Haku]
  def getHakukohde(oid: String): Either[Throwable, Hakukohde]
  def getHakukohdes(oids: Seq[String]): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdesForHaku(hakuOid: String): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdeOids(hakuOid:String): Either[Throwable, Seq[String]]
  def getArbitraryPublishedHakukohdeOid(oid: String): Either[Throwable, String]
  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]]
}

object HakuService {
  def apply(appConfig: AppConfig): HakuService = appConfig match {
    case _:StubbedExternalDeps => HakuFixtures
    case _ => new CachedHakuService(new TarjontaHakuService(appConfig))
  }
}

case class Haku(oid: String, korkeakoulu: Boolean,
                käyttääSijoittelua: Boolean, varsinaisenHaunOid: Option[String], sisältyvätHaut: Set[String],
                hakuAjat: List[Hakuaika], koulutuksenAlkamiskausi: Option[Kausi], yhdenPaikanSaanto: YhdenPaikanSaanto,
                nimi: Map[String, String])
case class Hakuaika(hakuaikaId: String, alkuPvm: Option[Long], loppuPvm: Option[Long]) {
  def hasStarted = alkuPvm match {
    case Some(alku) => new DateTime().isAfter(new DateTime(alku))
    case _ => true
  }
}

case class Hakukohde(oid: String, hakuOid: String, hakukohdeKoulutusOids: List[String],
                     koulutusAsteTyyppi: String, koulutusmoduuliTyyppi: String,
                     hakukohteenNimet: Map[String, String], tarjoajaNimet: Map[String, String], yhdenPaikanSaanto: YhdenPaikanSaanto,
                     tutkintoonJohtava:Boolean, koulutuksenAlkamiskausiUri:String, koulutuksenAlkamisvuosi:Int)

case class Koulutus(oid: String, koulutuksenAlkamiskausi: Kausi, tila: String, johtaaTutkintoon: Boolean)

class KoulutusSerializer extends CustomSerializer[Koulutus]((formats: Formats) => {
  implicit val f = formats
  ( {
    case o: JObject =>
      val JString(oid) = o \ "oid"
      val JString(tila) = o \ "tila"
      val JInt(vuosi) = o \ "koulutuksenAlkamisvuosi"
      val JBool(johtaaTutkintoon) = o \ "johtaaTutkintoon"
      val kausi = o \ "koulutuksenAlkamiskausi" \ "uri" match {
        case JString("kausi_k") => Kevat(vuosi.toInt)
        case JString("kausi_s") => Syksy(vuosi.toInt)
        case x => throw new MappingException(s"Unrecognized kausi URI $x")
      }
      val koulutusUriOpt = (o \ "koulutuskoodi" \ "uri").extractOpt[String]
      val koulutusVersioOpt = (o \ "koulutuskoodi" \ "versio").extractOpt[Int]
      Koulutus(oid, kausi, tila, johtaaTutkintoon)
  }, { case o => ??? })
})

protected trait JsonHakuService {
  import org.json4s._
  implicit val formats = DefaultFormats ++ List(new KoulutusSerializer)

  protected def toHaku(haku: HakuTarjonnassa) = {
    val korkeakoulu: Boolean = haku.kohdejoukkoUri.startsWith("haunkohdejoukko_12#")
    val yhteishaku: Boolean = haku.hakutapaUri.startsWith("hakutapa_01#")
    val varsinainenhaku: Boolean = haku.hakutyyppiUri.startsWith("hakutyyppi_01#1")
    val lisähaku: Boolean = haku.hakutyyppiUri.startsWith("hakutyyppi_03#1")
    val koulutuksenAlkamisvuosi = haku.koulutuksenAlkamisVuosi
    val kausi = if (haku.koulutuksenAlkamiskausiUri.isDefined && haku.koulutuksenAlkamisVuosi.isDefined) {
      if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_k")) {
            Some(Kevat(koulutuksenAlkamisvuosi.get))
          } else if (haku.koulutuksenAlkamiskausiUri.get.startsWith("kausi_s")) {
            Some(Syksy(koulutuksenAlkamisvuosi.get))
          } else throw new MappingException(s"Haku ${haku.oid} has unrecognized kausi URI '${haku.koulutuksenAlkamiskausiUri.get}' . Full data of haku: $haku")
    } else None

    Haku(haku.oid, korkeakoulu, haku.sijoittelu, haku.parentHakuOid,
      haku.sisaltyvatHaut, haku.hakuaikas, kausi, haku.yhdenPaikanSaanto, haku.nimi)
  }
}

class CachedHakuService(wrappedService: HakuService) extends HakuService {
  private val byOid = TTLOptionalMemoize.memoize[String, Haku](oid => wrappedService.getHaku(oid), 4 * 60 * 60)
  private val all = TTLOptionalMemoize.memoize[Unit, List[Haku]](_ => wrappedService.kaikkiJulkaistutHaut, 4 * 60 * 60)

  override def getHaku(oid: String): Either[Throwable, Haku] = byOid(oid)
  override def getHakukohde(oid: String): Either[Throwable, Hakukohde] = wrappedService.getHakukohde(oid)
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
                                   nimi: Map[String, String]) {
  def julkaistu = {
    tila == "JULKAISTU"
  }
}

case class YhdenPaikanSaanto(voimassa: Boolean, syy: String)

class TarjontaHakuService(appConfig:AppConfig) extends HakuService with JsonHakuService with Logging {

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  def getHaku(oid: String): Either[Throwable, Haku] = {
    val url = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.haku", oid)
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
    val url = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.haku", hakuOid)
    fetch(url) { response =>
      (parse(response) \ "result" \ "hakukohdeOids" ).extract[List[String]]
    }
  }

  override def getArbitraryPublishedHakukohdeOid(hakuOid: String): Either[Throwable, String] = {
    ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.hakukohde.search", hakuOid)

    val url = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.hakukohde.search", hakuOid)
    fetch(url) { response =>
      (parse(response) \ "result" \ "tulokset" \ "tulokset" \ "oid" ).extractOpt[String]
    }.right.flatMap(_.toRight(new IllegalArgumentException(s"No hakukohde found for haku $hakuOid")))
  }
  def getHakukohdes(oids: Seq[String]): Either[Throwable, List[Hakukohde]] = {
    sequence(for{oid <- oids.toStream} yield getHakukohde(oid))
  }
  def getHakukohde(hakukohdeOid: String): Either[Throwable, Hakukohde] = {
    val hakukohdeUrl = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.hakukohde", hakukohdeOid)
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
    val url = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.find")
    fetch(url) { response =>
      val haut = (parse(response) \ "result").extract[List[HakuTarjonnassa]]
      haut.filter(_.julkaistu).map(toHaku(_))
    }
  }

  def getKoulutus(koulutusOid: String): Either[Throwable, Koulutus] = {
    val koulutusUrl = ValintarekisteriOphUrlProperties.ophProperties.url("tarjonta-service.koulutus", koulutusOid)
    fetch(koulutusUrl) { response =>
      (parse(response) \ "result").extract[Koulutus]
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
  private def sequence[A, B](xs: Stream[Either[B, A]]): Either[B, List[A]] = xs match {
    case Stream.Empty => Right(Nil)
    case Left(e)#::_ => Left(e)
    case Right(x)#::rest => sequence(rest).right.map(x +: _)
  }
}
