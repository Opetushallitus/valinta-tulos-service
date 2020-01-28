package fi.vm.sade.valintatulosservice.tarjonta

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS

import fi.vm.sade.properties.OphProperties
import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.koodisto.{Koodi, KoodistoService}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.http4s.json4s.native.jsonExtract
import org.http4s.Method.GET
import org.http4s.{Request, Uri}
import org.joda.time.DateTime
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, DefaultFormats, Formats, MappingException}
import scalaj.http.HttpOptions
import scalaz.concurrent.Task

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

trait HakuService {
  def getHaku(oid: HakuOid): Either[Throwable, Haku]
  def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]]
  def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde]
  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]]
  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]]
}

case class HakuServiceConfig(ophProperties: OphProperties, stubbedExternalDeps: Boolean)

object HakuService {
  def apply(appConfig: AppConfig, casClient: CasClient, organisaatioService: OrganisaatioService, koodistoService: KoodistoService): HakuService = {
    val config = appConfig.hakuServiceConfig
    if (config.stubbedExternalDeps) {
      HakuFixtures
    } else {
      new CachedHakuService(new TarjontaHakuService(config), new KoutaHakuService(appConfig, casClient, organisaatioService, koodistoService), appConfig)
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
                     koulutuksenAlkamiskausiUri: Option[String],
                     koulutuksenAlkamisvuosi: Option[Int],
                     organisaatioRyhmaOids: Set[String]) {
  def kkTutkintoonJohtava: Boolean = kkHakukohde && tutkintoonJohtava
  def kkHakukohde: Boolean = koulutusAsteTyyppi == "KORKEAKOULUTUS"

  def koulutuksenAlkamiskausi: Option[Kausi] = (koulutuksenAlkamiskausiUri, koulutuksenAlkamisvuosi) match {
    case (Some(uri), Some(alkamisvuosi)) if uri.matches("""kausi_k#\d+""") => Some(Kevat(alkamisvuosi))
    case (Some(uri), Some(alkamisvuosi)) if uri.matches("""kausi_s#\d+""") => Some(Syksy(alkamisvuosi))
    case _ => None
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

class CachedHakuService(tarjonta: TarjontaHakuService, kouta: KoutaHakuService, config: AppConfig) extends HakuService {
  private val hakuCache = TTLOptionalMemoize.memoize[HakuOid, Haku](
    f = oid => tarjonta.getHaku(oid).left.flatMap(_ => kouta.getHaku(oid)),
    lifetimeSeconds = Duration(4, HOURS).toSeconds,
    maxSize = config.settings.estimatedMaxActiveHakus)

  private val kaikkiJulkaistutHautCache = TTLOptionalMemoize.memoize[Unit, List[Haku]](
    f = _ => tarjonta.kaikkiJulkaistutHaut.right.flatMap(tarjontaHaut => kouta.kaikkiJulkaistutHaut.right.map(_ ++ tarjontaHaut)),
    lifetimeSeconds = Duration(4, HOURS).toSeconds,
    maxSize = 1)

  override def getHaku(oid: HakuOid): Either[Throwable, Haku] = hakuCache(oid)

  override def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    tarjonta.getHakukohdeKela(oid).left.flatMap(_ => kouta.getHakukohdeKela(oid))
  }

  override def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    tarjonta.getHakukohde(oid).left.flatMap(_ => kouta.getHakukohde(oid))
  }

  override def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] = {
    MonadHelper.sequence(for { oid <- oids.toStream } yield getHakukohde(oid))
  }

  override def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    tarjonta.getHakukohdeOids(hakuOid).left.flatMap(_ => kouta.getHakukohdeOids(hakuOid))
  }

  override def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = kaikkiJulkaistutHautCache(())
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

  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, List[Hakukohde]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }
  def getHakukohdeKela(hakukohdeOid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    for {
      hakukohde <- getHakukohde(hakukohdeOid).right
      haku <- getHaku(hakukohde.hakuOid).right
      kelaHakukohde <- (if (haku.sallittuKohdejoukkoKelaLinkille) {
        val hakukohdeUrl = config.ophProperties.url(
          "tarjonta-service.hakukohdekela", hakukohdeOid)
        fetch(hakukohdeUrl) { response =>
          Some(parse(response).extract[HakukohdeKela])
        }.left.map {
          case e: IllegalArgumentException => new IllegalArgumentException(s"No hakukohde $hakukohdeOid ($hakukohdeUrl) found", e)
          case e: IllegalStateException => new IllegalStateException(s"Parsing hakukohde $hakukohdeOid ($hakukohdeUrl) failed", e)
          case e: Exception => new RuntimeException(s"Failed to get hakukohde $hakukohdeOid ($hakukohdeUrl)", e)
        }
      } else {
        Right(None)
      }).right
    } yield kelaHakukohde
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

case class KoutaHakuaika(alkaa: String,
                         paattyy: String) {
  def toHakuaika: Hakuaika = {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("Europe/Helsinki"))
    Hakuaika(
      hakuaikaId = "kouta-hakuaika-id",
      alkuPvm = Some(Instant.from(formatter.parse(alkaa)).toEpochMilli),
      loppuPvm = Some(Instant.from(formatter.parse(paattyy)).toEpochMilli)
    )
  }
}

case class KoutaHaku(oid: String,
                     nimi: Map[String, String],
                     kohdejoukkoKoodiUri: String,
                     kohdejoukonTarkenneKoodiUri: Option[String],
                     hakuajat: List[KoutaHakuaika],
                     alkamisvuosi: Option[String],
                     alkamiskausiKoodiUri: Option[String]) {
  def toHaku: Either[Throwable, Haku] = {
    for {
      alkamiskausi <- ((alkamiskausiKoodiUri, alkamisvuosi.map(s => (s, Try(s.toInt)))) match {
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_k#") => Right(Some(Kevat(vuosi)))
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_s#") => Right(Some(Syksy(vuosi)))
        case (Some(uri), Some((_, Success(_)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamiskausi URI $uri"))
        case (Some(_), Some((s, Failure(t)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamisvuosi $s", t))
        case _ => Right(None)
      }).right
    } yield Haku(
      oid = HakuOid(oid),
      korkeakoulu = kohdejoukkoKoodiUri.startsWith("haunkohdejoukko_12#"),
      toinenAste = List("haunkohdejoukko_11#", "haunkohdejoukko_17#", "haunkohdejoukko_20#").exists(kohdejoukkoKoodiUri.startsWith),
      sallittuKohdejoukkoKelaLinkille = !List("haunkohdejoukontarkenne_2#", "haunkohdejoukontarkenne_4#", "haunkohdejoukontarkenne_5#", "haunkohdejoukontarkenne_6#").exists(kiellettyTarkenne => kohdejoukonTarkenneKoodiUri.exists(_.startsWith(kiellettyTarkenne))),
      käyttääSijoittelua = false, // FIXME
      käyttääHakutoiveidenPriorisointia = false, // FIXME
      varsinaisenHaunOid = None,
      sisältyvätHaut = Set.empty,
      hakuAjat = hakuajat.map(_.toHakuaika),
      koulutuksenAlkamiskausi = alkamiskausi,
      yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, syy = "Yhden paikan sääntö Kouta:ssa aina hakukohdekohtainen"),
      nimi = nimi.map { case (lang, text) => ("kieli_" + lang) -> text })
  }
}

case class KoutaKoulutusMetadata(opintojenLaajuusKoodiUri: Option[String])

case class KoutaKoulutus(oid: String,
                         johtaaTutkintoon: Boolean,
                         koulutusKoodiUri: Option[String],
                         koulutustyyppi: Option[String],
                         metadata: Option[KoutaKoulutusMetadata])

case class KoutaToteutus(oid: String,
                         koulutusOid: String,
                         tarjoajat: List[String])

case class KoutaHakukohde(oid: String,
                          hakuOid: String,
                          tarjoajat: Option[List[String]],
                          nimi: Map[String, String],
                          kaytetaanHaunAlkamiskautta: Boolean,
                          alkamiskausiKoodiUri: Option[String],
                          alkamisvuosi: Option[String],
                          tila: String,
                          toteutusOid: String,
                          yhdenPaikanSaanto: YhdenPaikanSaanto) {
  def tarjoajat(toteutus: KoutaToteutus): Set[String] = {
    tarjoajat.filter(_.nonEmpty).getOrElse(toteutus.tarjoajat).toSet
  }

  def toHakukohde(haku: Option[KoutaHaku],
                  toteutus: KoutaToteutus,
                  koulutus: KoutaKoulutus,
                  tarjoajaorganisaatiot: List[Organisaatio]): Either[Throwable, Hakukohde] = {
    for {
      tarjoaja <- tarjoajaorganisaatiot.headOption
        .toRight(new IllegalStateException(s"Could not find tarjoaja for hakukohde $oid")).right
      koulutuksenAlkamisvuosi <- ((if (kaytetaanHaunAlkamiskautta) { haku.get.alkamisvuosi } else { alkamisvuosi }).map(s => (s, Try(s.toInt))) match {
        case Some((_, Success(vuosi))) => Right(Some(vuosi))
        case Some((s, Failure(t))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamisvuosi $s", t))
        case None => Right(None)
      }).right
    } yield Hakukohde(
      oid = HakukohdeOid(oid),
      hakuOid = HakuOid(hakuOid),
      tarjoajaOids = tarjoajat(toteutus),
      koulutusAsteTyyppi = if (List("yo", "amk").exists(koulutus.koulutustyyppi.contains(_))) { "KORKEAKOULUTUS" } else { "" },
      hakukohteenNimet = nimi.map { case (lang, text) => ("kieli_" + lang) -> text },
      tarjoajaNimet = tarjoaja.nimi,
      yhdenPaikanSaanto = yhdenPaikanSaanto,
      tutkintoonJohtava = koulutus.johtaaTutkintoon,
      koulutuksenAlkamiskausiUri = if (kaytetaanHaunAlkamiskautta) { haku.get.alkamiskausiKoodiUri } else { alkamiskausiKoodiUri },
      koulutuksenAlkamisvuosi = koulutuksenAlkamisvuosi,
      organisaatioRyhmaOids = Set.empty // FIXME
    )
  }

  def toHakukohdeKela(haku: Option[KoutaHaku],
                      toteutus: KoutaToteutus,
                      koulutus: KoutaKoulutus,
                      koulutuskoodi: Option[Koodi],
                      opintojenLaajuusKoodi: Option[Koodi],
                      tarjoajaorganisaatiohierarkiat: List[Organisaatiot]): Either[Throwable, HakukohdeKela] = {
    val koulutuksenAlkamiskausiUri = if (kaytetaanHaunAlkamiskautta) { haku.get.alkamiskausiKoodiUri } else { alkamiskausiKoodiUri }
    val koulutuksenAlkamisvuosi = if (kaytetaanHaunAlkamiskautta) { haku.get.alkamisvuosi } else { alkamisvuosi }
    for {
      oppilaitos <- tarjoajaorganisaatiohierarkiat.toStream.map(_.find(_.organisaatiotyypit.contains("OPPILAITOS"))).collectFirst {
        case Some(oppilaitos) => oppilaitos
      }.toRight(new IllegalStateException(s"Could not find oppilaitos for hakukohde $oid")).right
      oppilaitoskoodi <- oppilaitos.oppilaitosKoodi
        .toRight(new IllegalStateException(s"Could not find oppilaitoskoodi for oppilaitos ${oppilaitos.oid}")).right
      koulutuksenAlkamiskausi <- ((koulutuksenAlkamiskausiUri, koulutuksenAlkamisvuosi.map(s => (s, Try(s.toInt)))) match {
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_k#") => Right(Some(Kevat(vuosi)))
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_s#") => Right(Some(Syksy(vuosi)))
        case (Some(uri), Some((_, Success(_)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamiskausi URI $uri"))
        case (Some(_), Some((s, Failure(t)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamisvuosi $s", t))
        case _ => Right(None)
      }).right
    } yield HakukohdeKela(
      koulutuksenAlkamiskausi = koulutuksenAlkamiskausi,
      hakukohdeOid = oid,
      tarjoajaOid = oppilaitos.oid,
      oppilaitoskoodi = oppilaitoskoodi,
      koulutuslaajuusarvot = Seq(KoulutusLaajuusarvo(
        oid = Some(koulutus.oid),
        koulutuskoodi = koulutuskoodi.map(_.arvo),
        koulutustyyppi = koulutuskoodi.flatMap(_.findSisaltyvaKoodi("koulutustyyppi")).map(_.arvo),
        opintojenLaajuusarvo = opintojenLaajuusKoodi.map(_.arvo)
      ))
    )
  }
}

class KoutaHakuService(config: AppConfig, casClient: CasClient, organisaatioService: OrganisaatioService, koodistoService: KoodistoService) extends HakuService with Logging {
  private implicit val jsonFormats: Formats = DefaultFormats
  private val client = CasAuthenticatingClient(
    casClient = casClient,
    casParams = CasParams(
      config.ophUrlProperties.url("kouta-internal.service"),
      "auth/login",
      config.settings.koutaUsername,
      config.settings.koutaPassword
    ),
    serviceClient = org.http4s.client.blaze.defaultClient,
    clientCallerId = config.settings.callerId,
    sessionCookieName = "session"
  )

  def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    getKoutaHaku(oid).right.flatMap(_.toHaku)
  }

  def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    if (false) {
      for {
        koutaHakukohde <- getKoutaHakukohde(oid).right
        koutaHaku <- (if (koutaHakukohde.kaytetaanHaunAlkamiskautta) {
          getKoutaHaku(HakuOid(koutaHakukohde.hakuOid)).right.map(Some(_))
        } else {
          Right(None)
        }).right
        koutaToteutus <- getKoutaToteutus(koutaHakukohde.toteutusOid).right
        koutaKoulutus <- getKoutaKoulutus(koutaToteutus.koulutusOid).right
        koulutuskoodi <- koutaKoulutus.koulutusKoodiUri.fold[Either[Throwable, Option[Koodi]]](Right(None))(koodistoService.getKoodi(_).right.map(Some(_))).right
        opintojenlaajuuskoodi <- koutaKoulutus.metadata.flatMap(_.opintojenLaajuusKoodiUri).fold[Either[Throwable, Option[Koodi]]](Right(None))(koodistoService.getKoodi(_).right.map(Some(_))).right
        tarjoajaorganisaatiohierarkiat <- MonadHelper.sequence(koutaHakukohde.tarjoajat(koutaToteutus).map(organisaatioService.hae)).right
        hakukohde <- koutaHakukohde.toHakukohdeKela(koutaHaku, koutaToteutus, koutaKoulutus, koulutuskoodi, opintojenlaajuuskoodi, tarjoajaorganisaatiohierarkiat).right
      } yield hakukohde
    }
    getKoutaHakukohde(oid).right.map(_ => None)
  }

  def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    for {
      koutaHakukohde <- getKoutaHakukohde(oid).right
      koutaHaku <- (if (koutaHakukohde.kaytetaanHaunAlkamiskautta) { getKoutaHaku(HakuOid(koutaHakukohde.hakuOid)).right.map(Some(_)) } else { Right(None) }).right
      koutaToteutus <- getKoutaToteutus(koutaHakukohde.toteutusOid).right
      koutaKoulutus <- getKoutaKoulutus(koutaToteutus.koulutusOid).right
      tarjoajaorganisaatiot <- MonadHelper.sequence(koutaHakukohde.tarjoajat(koutaToteutus).map(getOrganisaatio)).right
      hakukohde <- koutaHakukohde.toHakukohde(koutaHaku, koutaToteutus, koutaKoulutus, tarjoajaorganisaatiot).right
    } yield hakukohde
  }

  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }

  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    getKoutaHaunHakukohteet(hakuOid).right.map(_.map(h => HakukohdeOid(h.oid)))
  }

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    Right(List.empty) // FIXME
  }

  private def getKoutaHaku(oid: HakuOid): Either[Throwable, KoutaHaku] = {
    fetch[KoutaHaku](config.ophUrlProperties.url("kouta-internal.haku", oid.toString))
  }

  private def getKoutaHakukohde(oid: HakukohdeOid): Either[Throwable, KoutaHakukohde] = {
    fetch[KoutaHakukohde](config.ophUrlProperties.url("kouta-internal.hakukohde", oid.toString))
  }

  private def getKoutaHaunHakukohteet(oid: HakuOid): Either[Throwable, Seq[KoutaHakukohde]] = {
    val query = Map("haku" -> oid.toString)
    fetch[List[KoutaHakukohde]](config.ophUrlProperties.url("kouta-internal.hakukohde.search", query))
  }

  private def getKoutaToteutus(oid: String): Either[Throwable, KoutaToteutus] = {
    fetch[KoutaToteutus](config.ophUrlProperties.url("kouta-internal.toteutus", oid))
  }

  private def getKoutaKoulutus(oid: String): Either[Throwable, KoutaKoulutus] = {
    fetch[KoutaKoulutus](config.ophUrlProperties.url("kouta-internal.koulutus", oid))
  }

  private def getOrganisaatio(oid: String): Either[Throwable, Organisaatio] = {
    organisaatioService.hae(oid).right
      .flatMap(_.find(_.oid == oid).toRight(new IllegalArgumentException(s"Could not find organisation $oid")))
  }

  private def fetch[T](url: String)(implicit manifest: Manifest[T]): Either[Throwable, T] = {
    Uri.fromString(url).flatMap(uri => client.fetch(Request(method = GET, uri = uri)) {
      case r if r.status.code == 200 =>
        r.as[T](jsonExtract[T])
          .handleWith {
            case t => r.bodyAsText.runLog
              .map(_.mkString)
              .flatMap(body => Task.fail(new IllegalStateException(s"Parsing result $body of GET $url failed", t)))
          }
      case r if r.status.code == 404 =>
        r.bodyAsText.runLog
          .map(_.mkString)
          .flatMap(body => Task.fail(new IllegalArgumentException(s"GET $url failed with status 404: $body")))
      case r =>
        r.bodyAsText.runLog
          .map(_.mkString)
          .flatMap(body => Task.fail(new RuntimeException(s"GET $url failed with status ${r.status.code}: $body")))
    }.unsafePerformSyncAttemptFor(Duration(1, TimeUnit.MINUTES))).toEither
  }
}
