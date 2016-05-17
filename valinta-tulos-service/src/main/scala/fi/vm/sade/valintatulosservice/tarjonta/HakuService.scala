package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.AppConfig.{AppConfig, StubbedExternalDeps}
import fi.vm.sade.valintatulosservice.domain.{Kausi, Kevat, Syksy}
import fi.vm.sade.valintatulosservice.koodisto.{Koodi, KoodiUri, KoodistoService}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import org.joda.time.DateTime
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, Formats, MappingException}

import scala.util.Try
import scalaj.http.HttpOptions

trait HakuService {
  def getHaku(oid: String): Option[Haku]
  def getHakukohde(oid: String): Option[Hakukohde]
  def getKoulutus(koulutusOid: String): Option[Koulutus]
  def getHakukohdeOids(hakuOid:String): Seq[String]
  def getArbitraryPublishedHakukohdeOid(oid: String): Option[String]
  def findLiittyvatHaut(haku: Haku): Set[String] = {
    val parentHaut = haku.varsinaisenHaunOid.flatMap(getHaku(_).map(parentHaku => parentHaku.sisältyvätHaut + parentHaku.oid)).getOrElse(Nil)
    (haku.sisältyvätHaut ++ parentHaut).filterNot(_ == haku.oid)
  }
  def kaikkiJulkaistutHaut: List[Haku]
}

object HakuService {
  def apply(koodistoService: KoodistoService, appConfig: AppConfig): HakuService = appConfig match {
    case _:StubbedExternalDeps => HakuFixtures
    case _ => new CachedHakuService(new TarjontaHakuService(koodistoService, appConfig))
  }
}

case class Haku(oid: String, korkeakoulu: Boolean, yhteishaku: Boolean, varsinainenhaku: Boolean, lisähaku: Boolean,
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
                     hakukohteenNimet: Map[String, String], tarjoajaNimet: Map[String, String])

case class Koulutus(oid: String, koulutuksenAlkamiskausi: Kausi, tila: String, koulutusKoodi: Option[Koodi]) {
  def johtaaTutkintoon: Boolean = {
    koulutusKoodi.exists { koodi =>
      val relaatiot = koodi.relaatiot
        .getOrElse(throw new IllegalStateException(s"Koulutus $this is missing koodi relations"))
      val tutkintoonjohtavuus = relaatiot.includes.find(_.uri.koodistoUri == KoodistoService.TutkintooJohtavaKoulutus)
        .getOrElse(throw new IllegalStateException(s"koodi $koodi is missing tutkintoonjohtavuus relation"))
      tutkintoonjohtavuus.uri == KoodistoService.OnTutkinto
    }
  }
}

class KoulutusSerializer extends CustomSerializer[Koulutus]((formats: Formats) => {
  implicit val f = formats
  ( {
    case o: JObject =>
      val JString(oid) = o \ "oid"
      val JString(tila) = o \ "tila"
      val JInt(vuosi) = o \ "koulutuksenAlkamisvuosi"
      val kausi = o \ "koulutuksenAlkamiskausi" \ "uri" match {
        case JString("kausi_k") => Kevat(vuosi.toInt)
        case JString("kausi_s") => Syksy(vuosi.toInt)
        case x => throw new MappingException(s"Unrecognized kausi URI $x")
      }
      val koulutusUriOpt = (o \ "koulutuskoodi" \ "uri").extractOpt[String]
      val koulutusVersioOpt = (o \ "koulutuskoodi" \ "versio").extractOpt[Int]
      val koodi: Option[Koodi] = for {
        koulutusUri <- koulutusUriOpt
        koulutusVersio <- koulutusVersioOpt
      } yield Koodi(KoodiUri(koulutusUri), koulutusVersio, None)

      Koulutus(oid, kausi, tila, koodi)
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

    Haku(haku.oid, korkeakoulu, yhteishaku, varsinainenhaku, lisähaku, haku.sijoittelu, haku.parentHakuOid,
      haku.sisaltyvatHaut, haku.hakuaikas, kausi, haku.yhdenPaikanSaanto, haku.nimi)
  }
}

class CachedHakuService(wrappedService: HakuService) extends HakuService {
  private val byOid = TTLOptionalMemoize.memoize(wrappedService.getHaku _, 4 * 60 * 60)
  private val all: (String) => Option[List[Haku]] = TTLOptionalMemoize.memoize({any : String => Some(wrappedService.kaikkiJulkaistutHaut)}, 4 * 60 * 60)

  override def getHaku(oid: String) = byOid(oid)
  override def getHakukohde(oid: String): Option[Hakukohde] = wrappedService.getHakukohde(oid)
  override def getHakukohdeOids(hakuOid:String): Seq[String] = wrappedService.getHakukohdeOids(hakuOid)
  override def getArbitraryPublishedHakukohdeOid(oid: String) = wrappedService.getArbitraryPublishedHakukohdeOid(oid)

  def kaikkiJulkaistutHaut: List[Haku] = all("").toList.flatten

  override def getKoulutus(koulutusOid: String): Option[Koulutus] = wrappedService.getKoulutus(koulutusOid)
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

class TarjontaHakuService(koodistoService: KoodistoService, appConfig:AppConfig) extends HakuService with JsonHakuService with Logging {

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  def getHaku(oid: String): Option[Haku] = {
    val url = appConfig.settings.tarjontaUrl + "/rest/v1/haku/" + oid
    fetch(url) { response =>
      val hakuTarjonnassa = (parse(response) \ "result").extract[HakuTarjonnassa]
      toHaku(hakuTarjonnassa)
    }
  }

  def getHakukohdeOids(hakuOid:String): Seq[String] = {
    val url = appConfig.settings.tarjontaUrl + "/rest/v1/haku/" + hakuOid
    fetch(url) { response =>
      (parse(response) \ "result" \ "hakukohdeOids" ).extract[List[String]]
    }.getOrElse(Nil)
  }

  override def getArbitraryPublishedHakukohdeOid(hakuOid: String): Option[String] = {
    val url = appConfig.settings.tarjontaUrl + s"/rest/v1/hakukohde/search?tila=VALMIS&tila=JULKAISTU&hakuOid=$hakuOid&offset=0&limit=1"
    fetch(url) { response =>
      (parse(response) \ "result" \ "tulokset" \ "tulokset" \ "oid" ).extractOpt[String]
    }.flatten
  }

  def getHakukohde(hakukohdeOid: String): Option[Hakukohde] = {
    val hakukohdeUrl = s"${appConfig.settings.tarjontaUrl}/rest/v1/hakukohde/$hakukohdeOid"
    fetch(hakukohdeUrl) { response => (parse(response) \ "result").extract[Hakukohde] }
  }

  def kaikkiJulkaistutHaut = {
    val url = appConfig.settings.tarjontaUrl + "/rest/v1/haku/find?addHakuKohdes=false"
    fetch(url) { response =>
      val haut = (parse(response) \ "result").extract[List[HakuTarjonnassa]]
      haut.filter(_.julkaistu).map(toHaku(_))
    }.getOrElse(Nil)
  }

  def getKoulutus(koulutusOid: String): Option[Koulutus] = {
    val koulutusUrl = s"${appConfig.settings.tarjontaUrl}/rest/v1/koulutus/$koulutusOid"
    val koulutusOption = fetch(koulutusUrl) { response => (parse(response) \ "result").extract[Koulutus] }
    koulutusOption.map { koulutus =>
      koulutus.koulutusKoodi match {
        case Some(koodi) => koulutus.copy(koulutusKoodi = koodistoService.fetchLatest(koodi.uri))
        case None => koulutus
      }
    }
  }

  private def fetch[T](url: String)(parse: (String => T)): Option[T] = {
    val (responseCode, _, resultString) = DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(30000),
      HttpOptions.readTimeout(120000)
    ).responseWithHeaders

    responseCode match {
      case 200 => {
        parseStatus(resultString) match {
          case Some(status) if status.equals("NOT_FOUND") => None
          case _ => {
            val parsed = Try(parse(resultString))
            if(parsed.isFailure) logger.error(s"Error parsing response from: $resultString", parsed.failed.get)
            parsed.toOption
          }
        }
      }
      case _ =>
        logger.warn("Get haku from " + url + " failed with status " + responseCode)
        None
    }
  }
}
