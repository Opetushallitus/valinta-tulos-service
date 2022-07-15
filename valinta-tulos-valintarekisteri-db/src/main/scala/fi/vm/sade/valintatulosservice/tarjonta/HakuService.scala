package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.utils.Timer
import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import fi.vm.sade.utils.http.DefaultHttpClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.MonadHelper
import fi.vm.sade.valintatulosservice.config.{AppConfig, StubbedExternalDeps}
import fi.vm.sade.valintatulosservice.koodisto.{Koodi, KoodistoService}
import fi.vm.sade.valintatulosservice.memoize.TTLOptionalMemoize
import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, OhjausparametritService}
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.http4s.Method.GET
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.json4s.native.jsonExtract
import org.http4s.{Request, Uri}
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.json4s.jackson.JsonMethods._
import org.json4s.{CustomSerializer, DefaultFormats, Formats, MappingException}
import scalaj.http.HttpOptions
import scalaz.concurrent.Task

import java.util
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.HOURS
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class YhdenPaikanSaanto(voimassa: Boolean, syy: String)

case class Haku(oid: HakuOid,
                korkeakoulu: Boolean,
                toinenAste: Boolean,
                sallittuKohdejoukkoKelaLinkille: Boolean,
                käyttääSijoittelua: Boolean,
                käyttääHakutoiveidenPriorisointia: Boolean,
                varsinaisenHaunOid: Option[String],
                sisältyvätHaut: Set[String],
                koulutuksenAlkamiskausi: Option[Kausi],
                yhdenPaikanSaanto: YhdenPaikanSaanto,
                nimi: Map[String, String]) {

  val sijoitteluJaPriorisointi = käyttääSijoittelua && käyttääHakutoiveidenPriorisointia
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
    case (Some(uri), Some(alkamisvuosi)) if uri.startsWith("kausi_k") => Some(Kevat(alkamisvuosi))
    case (Some(uri), Some(alkamisvuosi)) if uri.startsWith("kausi_s") => Some(Syksy(alkamisvuosi))
    case _ => None
  }

  def organisaatioOiditAuktorisointiin: Set[String] = tarjoajaOids ++ organisaatioRyhmaOids
}

case class HakukohdeMigri(oid: HakukohdeOid,
                          hakuOid: HakuOid,
                          hakuNimi: Map[String, String],
                          hakukohteenNimi: Map[String, String],
                          koulutuksenAlkamiskausiUri: Option[String],
                          koulutuksenAlkamisvuosi: Option[Int],
                          organisaatioOid: String,
                          organisaatioNimi: Map[String, String],
                          toteutusOid: String,
                          toteutusNimi: Map[String, String]) {
  def koulutuksenAlkamiskausi: Option[String] = koulutuksenAlkamiskausiUri match {
    case Some(uri) if uri.startsWith("kausi_k") => Some("K")
    case Some(uri) if uri.startsWith("kausi_s") => Some("S")
    case _ => None
  }
}

case class HakukohdeKela(koulutuksenAlkamiskausi: Option[Kausi],
                         hakukohdeOid: String,
                         tarjoajaOid: String,
                         oppilaitoskoodi: String,
                         koulutuslaajuusarvot: Seq[KoulutusLaajuusarvo])

trait HakuService {
  def getHaku(oid: HakuOid): Either[Throwable, Haku]
  def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]]
  def getHakukohdeMigri(oid: HakukohdeOid): Either[Throwable, HakukohdeMigri]
  def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde]
  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]]
  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]]
  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]]
}

object HakuService {
  def apply(appConfig: AppConfig, casClient: CasClient, ohjausparametritService: OhjausparametritService, organisaatioService: OrganisaatioService, koodistoService: KoodistoService): HakuService = {
    if (appConfig.isInstanceOf[StubbedExternalDeps]) {
      HakuFixtures.config = appConfig
      HakuFixtures
    } else {
      new CachedHakuService(new TarjontaHakuService(appConfig), new KoutaHakuService(appConfig, casClient, ohjausparametritService, organisaatioService, koodistoService), appConfig)
    }
  }
}

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

  protected def toHaku(haku: HakuTarjonnassa, config: AppConfig): Haku = {
    val korkeakoulu = config.settings.kohdejoukotKorkeakoulu.exists(s => haku.kohdejoukkoUri.startsWith(s + "#"))
    val sallittuKohdejoukkoKelaLinkille = !haku.kohdejoukonTarkenne.exists(tarkenne => config.settings.kohdejoukonTarkenteetAmkOpe.exists(s => tarkenne.startsWith(s + "#")))
    val toinenAste = config.settings.kohdejoukotToinenAste.exists(s => haku.kohdejoukkoUri.startsWith(s + "#"))
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
      koulutuksenAlkamiskausi = kausi,
      yhdenPaikanSaanto = haku.yhdenPaikanSaanto,
      nimi = haku.nimi)
  }
}

class CachedHakuService(tarjonta: TarjontaHakuService, kouta: KoutaHakuService, config: AppConfig) extends HakuService with Logging {
  private val hakuCache = TTLOptionalMemoize.memoize[HakuOid, Haku](
    f = oid => tarjonta.getHaku(oid).left.flatMap(_ => kouta.getHaku(oid)),
    lifetimeSeconds = Duration(4, HOURS).toSeconds,
    maxSize = config.settings.estimatedMaxActiveHakus)

  private val kaikkiJulkaistutHautCache = TTLOptionalMemoize.memoize[Unit, List[Haku]](
    f = _ => tarjonta.kaikkiJulkaistutHaut.right.flatMap(tarjontaHaut => kouta.kaikkiJulkaistutHaut.right.map(_ ++ tarjontaHaut)),
    lifetimeSeconds = Duration(4, HOURS).toSeconds,
    maxSize = 1)

  val KOUTA_OID_LENGTH = 35

  override def getHaku(oid: HakuOid): Either[Throwable, Haku] = hakuCache(oid)

  override def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    oid.toString match {
      case hakukohdeOid if hakukohdeOid.length == KOUTA_OID_LENGTH => kouta.getHakukohdeKela(oid)
      case _ => tarjonta.getHakukohdeKela(oid)
    }
  }

  override def getHakukohdeMigri(oid: HakukohdeOid): Either[Throwable, HakukohdeMigri] = {
    oid.toString match {
      case hakukohdeOid if hakukohdeOid.length == KOUTA_OID_LENGTH => kouta.getHakukohdeMigri(oid)
      case _ => tarjonta.getHakukohdeMigri(oid)
    }
  }

  override def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    oid.toString match {
      case hakukohdeOid if hakukohdeOid.length == KOUTA_OID_LENGTH => kouta.getHakukohde(oid)
      case _ => tarjonta.getHakukohde(oid)
    }
  }

  override def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] = {
    MonadHelper.sequence(for { oid <- oids.toStream } yield getHakukohde(oid))
  }

  override def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    hakuOid match {
      case hakuOid if hakuOid.toString.length == KOUTA_OID_LENGTH => kouta.getHakukohdeOids(hakuOid)
      case _ => tarjonta.getHakukohdeOids(hakuOid)
    }
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
                                   yhdenPaikanSaanto: YhdenPaikanSaanto,
                                   nimi: Map[String, String],
                                   organisaatioOids: Seq[String],
                                   tarjoajaOids: Seq[String]) {
  def julkaistu: Boolean = {
    tila == "JULKAISTU"
  }
}

class TarjontaHakuService(config: AppConfig) extends HakuService with JsonHakuService with Logging {

  def parseStatus(json: String): Option[String] = {
    for {
      status <- (parse(json) \ "status").extractOpt[String]
    } yield status
  }

  def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    val url = config.ophUrlProperties.url("tarjonta-service.haku", oid)
    fetch(url) { response =>
      val hakuTarjonnassa = (parse(response) \ "result").extract[HakuTarjonnassa]
      toHaku(hakuTarjonnassa, config)
    }.left.map {
      case e: IllegalArgumentException => new IllegalArgumentException(s"No haku $oid found", e)
      case e: IllegalStateException => new IllegalStateException(s"Parsing haku $oid failed", e)
      case e: Exception => new RuntimeException(s"Failed to get haku $oid", e)
    }
  }

  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    val url = config.ophUrlProperties.url("tarjonta-service.haku", hakuOid)
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
        val hakukohdeUrl = config.ophUrlProperties.url(
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

  def getHakukohdeMigri(hakukohdeOid: HakukohdeOid): Either[Throwable, HakukohdeMigri] = {
    throw new RuntimeException(s"Migri hakukohde from tarjonta not implemented, skipping. Hakukohdeoid: $hakukohdeOid.")
  }

  def getHakukohde(hakukohdeOid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    val hakukohdeUrl = config.ophUrlProperties.url(
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
    val url = config.ophUrlProperties.url("tarjonta-service.find", Map("addHakuKohdes" -> false).asJava)
    fetch(url) { response =>
      val haut = (parse(response) \ "result").extract[List[HakuTarjonnassa]]
      haut.filter(_.julkaistu).map(toHaku(_, config))
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

case class KoodiUri(koodiUri: String)

case class KoulutuksenAlkamiskausi(koulutuksenAlkamiskausi: Option[KoodiUri],
                                   koulutuksenAlkamisvuosi: Option[String]
                                  )

case class KoutaHakuMetadata(koulutuksenAlkamiskausi: Option[KoulutuksenAlkamiskausi])

case class KoutaHaku(oid: String,
                     nimi: Map[String, String],
                     kohdejoukkoKoodiUri: String,
                     kohdejoukonTarkenneKoodiUri: Option[String],
                     metadata: KoutaHakuMetadata) {
  def getKausiAndVuosi(metadata: KoutaHakuMetadata): (Option[String], Option[String]) = {
    val kausiUri = metadata.koulutuksenAlkamiskausi.flatMap(ak => ak.koulutuksenAlkamiskausi.map(ak => ak.koodiUri))
    val vuosi = metadata.koulutuksenAlkamiskausi.flatMap(ak => ak.koulutuksenAlkamisvuosi)
    (kausiUri, vuosi)
  }

  def toHaku(ohjausparametrit: Ohjausparametrit, config: AppConfig): Either[Throwable, Haku] = {
    val (alkamisKausiKoodiUri, alkamisVuosi) = getKausiAndVuosi(metadata)
    for {
      alkamiskausi <- ((alkamisKausiKoodiUri, alkamisVuosi.map(s => (s, Try(s.toInt)))) match {
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_k") => Right(Some(Kevat(vuosi)))
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_s") => Right(Some(Syksy(vuosi)))
        case (Some(uri), Some((_, Success(_)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamiskausi URI $uri"))
        case (Some(_), Some((s, Failure(t)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamisvuosi $s", t))
        case _ => Right(None)
      }).right
    } yield Haku(
      oid = HakuOid(oid),
      korkeakoulu = config.settings.kohdejoukotKorkeakoulu.exists(s => kohdejoukkoKoodiUri.startsWith(s + "#")),
      toinenAste = config.settings.kohdejoukotToinenAste.exists(s => kohdejoukkoKoodiUri.startsWith(s + "#")),
      sallittuKohdejoukkoKelaLinkille = !kohdejoukonTarkenneKoodiUri.exists(tarkenne => config.settings.kohdejoukonTarkenteetAmkOpe.exists(s => tarkenne.startsWith(s + "#"))),
      käyttääSijoittelua = ohjausparametrit.sijoittelu,
      käyttääHakutoiveidenPriorisointia = ohjausparametrit.jarjestetytHakutoiveet,
      varsinaisenHaunOid = None,
      sisältyvätHaut = Set.empty,
      koulutuksenAlkamiskausi = alkamiskausi,
      yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, syy = "Yhden paikan sääntö Kouta:ssa aina hakukohdekohtainen"),
      nimi = nimi.map { case (lang, text) => ("kieli_" + lang) -> text })
  }
}

case class KoutaKoulutusMetadata(opintojenLaajuusKoodiUri: Option[String])

case class KoutaKoulutus(oid: String,
                         johtaaTutkintoon: Boolean,
                         koulutusKoodiUrit: Set[String],
                         koulutustyyppi: String,
                         metadata: Option[KoutaKoulutusMetadata])

case class KoutaToteutusOpetustiedot(alkamiskausiKoodiUri: Option[String],
                                     alkamisvuosi: Option[String])

case class KoutaToteutusMetadata(opetus: Option[KoutaToteutusOpetustiedot])

case class KoutaToteutus(oid: String,
                         koulutusOid: String,
                         tarjoajat: List[String],
                         nimi: Map[String, String],
                         metadata: Option[KoutaToteutusMetadata])

case class PaateltyAlkamiskausi(
                                 source: String, //lähde-entiteetin oid (hakukohde, haku tai toteutus)
                                 kausiUri: String,
                                 vuosi: String
                               )

case class KoutaHakukohde(oid: String,
                          hakuOid: String,
                          tarjoaja: String,
                          nimi: Map[String, String],
                          kaytetaanHaunAlkamiskautta: Boolean,
                          alkamiskausiKoodiUri: Option[String],
                          alkamisvuosi: Option[String],
                          tila: String,
                          toteutusOid: String,
                          yhdenPaikanSaanto: YhdenPaikanSaanto,
                          paateltyAlkamiskausi: Option[PaateltyAlkamiskausi]) {

  def toHakukohde(koulutus: KoutaKoulutus,
                  tarjoaja: Organisaatio): Hakukohde = {
    Hakukohde(
      oid = HakukohdeOid(oid),
      hakuOid = HakuOid(hakuOid),
      tarjoajaOids = Set(tarjoaja.oid),
      koulutusAsteTyyppi = if (List("yo", "amk").exists(koulutus.koulutustyyppi.contains(_))) { "KORKEAKOULUTUS" } else { "" },
      hakukohteenNimet = nimi.map { case (lang, text) => ("kieli_" + lang) -> text },
      tarjoajaNimet = tarjoaja.nimi,
      yhdenPaikanSaanto = yhdenPaikanSaanto,
      tutkintoonJohtava = koulutus.johtaaTutkintoon,
      koulutuksenAlkamiskausiUri = paateltyAlkamiskausi.map(ak => ak.kausiUri),
      koulutuksenAlkamisvuosi = paateltyAlkamiskausi.map(ak => Integer.parseInt(ak.vuosi)),
      organisaatioRyhmaOids = Set.empty // FIXME
    )
  }

  def toHakukohdeMigri(haku: KoutaHaku,
                       toteutus: KoutaToteutus,
                       tarjoaja: Organisaatio): HakukohdeMigri = {
    HakukohdeMigri(
      oid = HakukohdeOid(oid),
      hakuOid = HakuOid(hakuOid),
      hakuNimi = haku.nimi,
      hakukohteenNimi = nimi,
      koulutuksenAlkamiskausiUri = paateltyAlkamiskausi.map(ak => ak.kausiUri),
      koulutuksenAlkamisvuosi = paateltyAlkamiskausi.map(ak => Integer.parseInt(ak.vuosi)),
      organisaatioOid = tarjoaja.oid,
      organisaatioNimi = tarjoaja.nimi,
      toteutusOid = toteutus.oid,
      toteutusNimi = toteutus.nimi
    )
  }

  def toHakukohdeKela(haku: Option[KoutaHaku],
                      koulutus: KoutaKoulutus,
                      koulutuskoodit: Set[Koodi],
                      opintojenLaajuusKoodi: Option[Koodi],
                      tarjoajaorganisaatiohierarkiat: List[Organisaatiot]): Either[Throwable, HakukohdeKela] = {
    val koulutuksenAlkamiskausiUri =
      if (kaytetaanHaunAlkamiskautta) {
        haku.get.metadata.koulutuksenAlkamiskausi.flatMap(ak => ak.koulutuksenAlkamiskausi.map(k => k.koodiUri))
      } else alkamiskausiKoodiUri
    val koulutuksenAlkamisvuosi =
      if (kaytetaanHaunAlkamiskautta) {
        haku.get.metadata.koulutuksenAlkamiskausi.flatMap(ak => ak.koulutuksenAlkamisvuosi)
      } else alkamisvuosi
    for {
      oppilaitos <- tarjoajaorganisaatiohierarkiat.toStream.map(_.find(_.organisaatiotyypit.contains("OPPILAITOS"))).collectFirst {
        case Some(oppilaitos) => oppilaitos
      }.toRight(new IllegalStateException(s"Could not find oppilaitos for hakukohde $oid")).right
      oppilaitoskoodi <- oppilaitos.oppilaitosKoodi
        .toRight(new IllegalStateException(s"Could not find oppilaitoskoodi for oppilaitos ${oppilaitos.oid}")).right
      koulutuksenAlkamiskausi <- ((koulutuksenAlkamiskausiUri, koulutuksenAlkamisvuosi.map(s => (s, Try(s.toInt)))) match {
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_k") => Right(Some(Kevat(vuosi)))
        case (Some(uri), Some((_, Success(vuosi)))) if uri.startsWith("kausi_s") => Right(Some(Syksy(vuosi)))
        case (Some(uri), Some((_, Success(_)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamiskausi URI $uri"))
        case (Some(_), Some((s, Failure(t)))) => Left(new IllegalStateException(s"Unrecognized koulutuksen alkamisvuosi $s", t))
        case _ => Right(None)
      }).right
    } yield HakukohdeKela(
      koulutuksenAlkamiskausi = koulutuksenAlkamiskausi,
      hakukohdeOid = oid,
      tarjoajaOid = oppilaitos.oid,
      oppilaitoskoodi = oppilaitoskoodi,
      koulutuslaajuusarvot = koulutuskoodit.map(koulutuskoodi =>
        KoulutusLaajuusarvo(
          oid = Some(koulutus.oid),
          koulutuskoodi = Some(koulutuskoodi.arvo),
          koulutustyyppi = koulutuskoodi.findSisaltyvaKoodi("koulutustyyppi").map(_.arvo),
          opintojenLaajuusarvo = opintojenLaajuusKoodi.map(_.arvo)
        )
      ).toSeq
    )
  }
}

class KoutaHakuService(config: AppConfig,
                       casClient: CasClient,
                       ohjausparametritService: OhjausparametritService,
                       organisaatioService: OrganisaatioService,
                       koodistoService: KoodistoService) extends HakuService with Logging {
  private implicit val jsonFormats: Formats = DefaultFormats

  private val hakuSingleCache = TTLOptionalMemoize.memoize[HakuOid, KoutaHaku](
    f = oid => getKoutaHaku(oid).left.flatMap(_ => getKoutaHaku(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = config.settings.koutaHakuServiceSingleEntityCacheSize)

  private val hakukohdeSingleCache = TTLOptionalMemoize.memoize[HakukohdeOid, KoutaHakukohde](
    f = oid => getKoutaHakukohde(oid).left.flatMap(_ => getKoutaHakukohde(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = config.settings.koutaHakuServiceSingleEntityCacheSize)

  private val koulutusSingleCache = TTLOptionalMemoize.memoize[String, KoutaKoulutus](
    f = oid => getKoutaKoulutus(oid).left.flatMap(_ => getKoutaKoulutus(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = config.settings.koutaHakuServiceSingleEntityCacheSize)

  private val toteutusSingleCache = TTLOptionalMemoize.memoize[String, KoutaToteutus](
    f = oid => getKoutaToteutus(oid).left.flatMap(_ => getKoutaToteutus(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = config.settings.koutaHakuServiceSingleEntityCacheSize)

  private val organisaatioSingleCache = TTLOptionalMemoize.memoize[String, Organisaatio](
    f = oid => getOrganisaatio(oid).left.flatMap(_ => getOrganisaatio(oid)),
    lifetimeSeconds = Duration(1, HOURS).toSeconds,
    maxSize = config.settings.koutaHakuServiceSingleEntityCacheSize)


  private val client = CasAuthenticatingClient(
    casClient = casClient,
    casParams = CasParams(
      config.ophUrlProperties.url("kouta-internal.service"),
      "auth/login",
      config.settings.koutaUsername,
      config.settings.koutaPassword
    ),
    serviceClient = SimpleHttp1Client(config.blazeDefaultConfig),
    clientCallerId = config.settings.callerId,
    sessionCookieName = "session"
  )

  def getKoutaHakuCached(oid: HakuOid): Either[Throwable, KoutaHaku] = hakuSingleCache(oid)
  def getKoutaHakukohdeCached(oid: HakukohdeOid): Either[Throwable, KoutaHakukohde] = hakukohdeSingleCache(oid)
  def getKoutaKoulutusCached(oid: String): Either[Throwable, KoutaKoulutus] = koulutusSingleCache(oid)
  def getKoutaToteutusCached(oid: String): Either[Throwable, KoutaToteutus] = toteutusSingleCache(oid)

  def getOrganisaatioCached(oid: String): Either[Throwable, Organisaatio] = organisaatioSingleCache(oid)

  def getHaku(oid: HakuOid): Either[Throwable, Haku] = {
    for {
      o <- ohjausparametritService.ohjausparametrit(oid).right
      kh <- getKoutaHaku(oid).right
      h <- kh.toHaku(o, config).right
    } yield h
  }

  def getHakukohdeKela(oid: HakukohdeOid): Either[Throwable, Option[HakukohdeKela]] = {
    for {
      koutaHakukohde <- getKoutaHakukohdeCached(oid).right
      koutaHaku <- (if (koutaHakukohde.kaytetaanHaunAlkamiskautta) {
        getKoutaHakuCached(HakuOid(koutaHakukohde.hakuOid)).right.map(Some(_))
      } else {
        Right(None)
      }).right
      koutaToteutus <- getKoutaToteutusCached(koutaHakukohde.toteutusOid).right
      koutaKoulutus <- getKoutaKoulutusCached(koutaToteutus.koulutusOid).right
      koulutuskoodi <- getKoulutusKoodit(koutaKoulutus.koulutusKoodiUrit).right
      opintojenlaajuuskoodi <- koutaKoulutus.metadata.flatMap(_.opintojenLaajuusKoodiUri).fold[Either[Throwable, Option[Koodi]]](Right(None))(koodistoService.getKoodi(_).right.map(Some(_))).right
      tarjoajaorganisaatiohierarkia <- organisaatioService.hae(koutaHakukohde.tarjoaja).right
      hakukohde <- koutaHakukohde.toHakukohdeKela(koutaHaku, koutaKoulutus, koulutuskoodi, opintojenlaajuuskoodi, List(tarjoajaorganisaatiohierarkia)).right
    } yield Some(hakukohde)
  }

  def getHakukohdeMigri(oid: HakukohdeOid): Either[Throwable, HakukohdeMigri] = {
    for {
      koutaHakukohde <- getKoutaHakukohdeCached(oid).right
      koutaHaku <- getKoutaHakuCached(HakuOid(koutaHakukohde.hakuOid)).right
      koutaToteutus <- getKoutaToteutusCached(koutaHakukohde.toteutusOid).right
      tarjoajaorganisaatio <- getOrganisaatioCached(koutaHakukohde.tarjoaja).right
    } yield koutaHakukohde.toHakukohdeMigri(koutaHaku, koutaToteutus, tarjoajaorganisaatio)
  }

  def getKoulutusKoodit(koulutusKoodiUrit: Set[String]): Either[Throwable, Set[Koodi]] = {
    Right(koulutusKoodiUrit.map(koodistoService.getKoodi(_) match {
      case Left(e) => return Left(e);
      case Right(koodi) => koodi
    }))
  }

  def getHakukohde(oid: HakukohdeOid): Either[Throwable, Hakukohde] = {
    logger.info(s"Get hakukohde, using cached singles $oid")
    for {
      koutaHakukohde <- Timer.timed(s"Kouta-internal get hakukohde: $oid", 1000)(getKoutaHakukohdeCached(oid).right)
      koutaToteutus <- Timer.timed(s"Kouta-internal get toteutus: ${koutaHakukohde.toteutusOid} ", 1000)(getKoutaToteutusCached(koutaHakukohde.toteutusOid).right)
      koutaKoulutus <- Timer.timed(s"Kouta-internal get koulutus: ${koutaToteutus.koulutusOid} ", 1000)(getKoutaKoulutusCached(koutaToteutus.koulutusOid).right)
      tarjoajaorganisaatio <- Timer.timed(s"Kouta-internal get organisaatio: ${koutaHakukohde.tarjoaja} ", 1000)(getOrganisaatioCached(koutaHakukohde.tarjoaja).right)
    } yield koutaHakukohde.toHakukohde(koutaKoulutus, tarjoajaorganisaatio)
  }

  def getHakukohdes(oids: Seq[HakukohdeOid]): Either[Throwable, Seq[Hakukohde]] = {
    MonadHelper.sequence(for {oid <- oids.toStream} yield getHakukohde(oid))
  }

  def getHakukohdeOids(hakuOid: HakuOid): Either[Throwable, Seq[HakukohdeOid]] = {
    getKoutaHaunHakukohteet(hakuOid).right.map(_.map(h => HakukohdeOid(h.oid)))
  }

  def kaikkiJulkaistutHaut: Either[Throwable, List[Haku]] = {
    fetch[List[KoutaHaku]](config.ophUrlProperties.url("kouta-internal.haku.search")).right
      .flatMap(koutaHaut => MonadHelper.sequence(koutaHaut.map(koutaHaku => {
        val ohjausparametrit = ohjausparametritService.ohjausparametrit(HakuOid(koutaHaku.oid)) match {
          case Right(o) => o
          case Left(e) => throw e
        }
        koutaHaku.toHaku(ohjausparametrit,config)
      })))
  }

  private def getKoutaHaku(oid: HakuOid): Either[Throwable, KoutaHaku] = {
    fetch[KoutaHaku](config.ophUrlProperties.url("kouta-internal.haku", oid.toString))
  }

  private def getKoutaHakukohde(oid: HakukohdeOid): Either[Throwable, KoutaHakukohde] = {
    fetch[KoutaHakukohde](config.ophUrlProperties.url("kouta-internal.hakukohde", oid.toString))
  }

  private def getKoutaHaunHakukohteet(oid: HakuOid): Either[Throwable, Seq[KoutaHakukohde]] = {
    val query = new util.HashMap[String, String]()
    query.put("haku", oid.toString)
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
