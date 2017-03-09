package fi.vm.sade.valintatulosservice.production

import java.io.File

import fi.vm.sade.utils.cas.CasClient.TGTUrl
import fi.vm.sade.utils.cas._
import fi.vm.sade.utils.http.DefaultHttpRequest
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.apache.commons.io.FileUtils
import org.http4s.client.Client
import org.http4s.dsl.{POST, resolve, uri}
import org.http4s.{Method, Request, Uri, client, _}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.language.experimental.macros
import scalaj.http.{Http, HttpOptions}
import scalaz.concurrent.Task
import org.http4s.Status.Created
import org.http4s.client._
import org.http4s.dsl._
import org.http4s.headers.{Location, `Set-Cookie`}

import scala.xml._
import scalaz.concurrent.Task
import scalaz.stream.{Channel, async, channel}



@Ignore
@RunWith(classOf[JUnitRunner])
class SijoitteluRestTest extends Specification with MatcherMacros with Logging with PerformanceLogger {
  val oldHost = "https://testi.virkailija.opintopolku.fi"
  //private val newHost = "https://testi.virkailija.opintopolku.fi"
  private val newHost = "http://localhost:8097"

  val cas_user = System.getProperty("cas_user")
  val cas_password = System.getProperty("cas_password")
  val cas_url = oldHost + "/cas"
  //val haku_oid = "1.2.246.562.29.75203638285" // Kevään 2016 kk-yhteishaku
  //val haku_oid = "1.2.246.562.29.14662042044" // Kevään 2016 2. asteen yhteishaku
  //val haku_oid = "1.2.246.562.29.95390561488" // Kevään 2015 kk-yhteishaku
  //val haku_oid = "1.2.246.562.29.28924613947" // Haku ammatilliseen opettajankoulutukseen 2017
//  val haku_oid = "1.2.246.562.29.87593180141" // Syksyn 2016 kk-yhteishaku
  val hakuOidsToTest = Seq("1.2.246.562.29.669559278110")

  val infoOn = true
  val debugOn = false
  val fileForStoringNewResponse: Option[String] = /*None  //  */ Some("/tmp/lol.json")

  def info(message:String) = if(infoOn) logger.info(message)
  def debug(message:String) = if(debugOn) logger.info(message)

  implicit val formats = DefaultFormats

  val vanhaSijoitteluCasClient = createCasClient("/sijoittelu-service")

  "New sijoittelu (valintarekisteri) and old sijoittelu (sijoitteluDb)" should {
    "contain same information" in {

      val vtsClient = new VtsAuthenticatingClient(oldHost, "/valinta-tulos-service", cas_user, cas_password)
      val vtsSessionCookie = vtsClient.getVtsSession(newHost)

      hakuOidsToTest.foreach { oid =>
        info(s"*** Tarkistetaan haku $oid")
        compareOldAndNewSijoitteluResults(oid)
        info(s"*** Haku $oid tsekattu.")
      }

      def compareOldAndNewSijoitteluResults(hakuOid: String) = {
        val uusiSijoittelu: Sijoitteluajo = time("Create uusi sijoittelu") {
          get[Sijoitteluajo](() => getNewSijoittelu(hakuOid, vtsSessionCookie))
        }
        val vanhaSijoittelu = time("Create vanha sijoittelu") {
          createVanhaSijoitteluajo(hakuOid)
        }

        info(s"Sijoittelut valmiina")
        uusiSijoittelu.sijoitteluajoId mustEqual vanhaSijoittelu.sijoitteluajoId
        uusiSijoittelu.hakuOid mustEqual vanhaSijoittelu.hakuOid
        uusiSijoittelu.startMils mustEqual vanhaSijoittelu.startMils
        uusiSijoittelu.endMils mustEqual vanhaSijoittelu.endMils
        uusiSijoittelu.hakukohteet.size mustEqual vanhaSijoittelu.hakukohteet.size

        var valintatapajonot = 0
        var hakemukset = 0
        var hakijaryhmat = 0

        info(s"Hakukohteita ${uusiSijoittelu.hakukohteet.size}")
        uusiSijoittelu.hakukohteet.foreach(uusiHakukohde => {
          debug(s"Hakukohde ${uusiHakukohde.oid}")
          val vanhaHakukohde = vanhaSijoittelu.hakukohteet.find(_.oid.equals(uusiHakukohde.oid)).get
          uusiHakukohde.sijoitteluajoId mustEqual vanhaHakukohde.sijoitteluajoId
          uusiHakukohde.tila mustEqual vanhaHakukohde.tila
          uusiHakukohde.tarjoajaOid mustEqual None // tarjoaja oids are only fetched from tarjonta on demand these days
          uusiHakukohde.kaikkiJonotSijoiteltu mustEqual vanhaHakukohde.kaikkiJonotSijoiteltu
          uusiHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual vanhaHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet

          uusiHakukohde.valintatapajonot.size mustEqual vanhaHakukohde.valintatapajonot.size
          valintatapajonot = valintatapajonot + uusiHakukohde.valintatapajonot.size
          uusiHakukohde.valintatapajonot.foreach(uusiValintatapajono => {
            debug(s"Valintatapajono ${uusiValintatapajono.oid}")
            val vanhaValintatapajono = vanhaHakukohde.valintatapajonot.find(_.oid.equals(uusiValintatapajono.oid)).get
            uusiValintatapajono must matchA[Valintatapajono]
              .tasasijasaanto(vanhaValintatapajono.tasasijasaanto)
              .tila(vanhaValintatapajono.tila)
              .prioriteetti(vanhaValintatapajono.prioriteetti)
              .aloituspaikat(vanhaValintatapajono.aloituspaikat)
              .alkuperaisetAloituspaikat(vanhaValintatapajono.alkuperaisetAloituspaikat)
              .alinHyvaksyttyPistemaara(vanhaValintatapajono.alinHyvaksyttyPistemaara)
              .eiVarasijatayttoa(vanhaValintatapajono.eiVarasijatayttoa)
              .kaikkiEhdonTayttavatHyvaksytaan(vanhaValintatapajono.kaikkiEhdonTayttavatHyvaksytaan)
              .poissaOlevaTaytto(vanhaValintatapajono.poissaOlevaTaytto)
              .hakeneet(vanhaValintatapajono.hakeneet)
              .hyvaksytty(vanhaValintatapajono.hyvaksytty)
              .varalla(vanhaValintatapajono.varalla)
              .varasijat(vanhaValintatapajono.varasijat)
              .varasijaTayttoPaivat(vanhaValintatapajono.varasijaTayttoPaivat)
              .varasijojaTaytetaanAsti(vanhaValintatapajono.varasijojaTaytetaanAsti)
              .tayttojono(vanhaValintatapajono.tayttojono)

            uusiValintatapajono.valintaesitysHyvaksytty mustEqual vanhaValintatapajono.valintaesitysHyvaksytty

            uusiValintatapajono.hakemukset.size mustEqual vanhaValintatapajono.hakemukset.size
            hakemukset = hakemukset + uusiValintatapajono.hakemukset.size
            uusiValintatapajono.hakemukset.foreach(uusiHakemus => {
              debug(s"Hakemus ${uusiHakemus.hakemusOid}")
              val vanhaHakemus = vanhaValintatapajono.hakemukset.find(_.hakemusOid.equals(uusiHakemus.hakemusOid)).get
              uusiHakemus must matchA[Hakemus]
                .hakijaOid(vanhaHakemus.hakijaOid)
                .pisteet(vanhaHakemus.pisteet)
                .paasyJaSoveltuvuusKokeenTulos(vanhaHakemus.paasyJaSoveltuvuusKokeenTulos)
                .etunimi(vanhaHakemus.etunimi)
                .sukunimi(vanhaHakemus.sukunimi)
                .prioriteetti(vanhaHakemus.prioriteetti)
                .jonosija(vanhaHakemus.jonosija)
                .tasasijaJonosija(vanhaHakemus.tasasijaJonosija)
                .tila(vanhaHakemus.tila)
                .hyvaksyttyHarkinnanvaraisesti(vanhaHakemus.hyvaksyttyHarkinnanvaraisesti)
                .varasijanNumero(vanhaHakemus.varasijanNumero)
                .valintatapajonoOid(vanhaHakemus.valintatapajonoOid)
                .hakuOid(vanhaHakemus.hakuOid)
                .onkoMuuttunutViimeSijoittelussa(vanhaHakemus.onkoMuuttunutViimeSijoittelussa)
                .siirtynytToisestaValintatapajonosta(vanhaHakemus.siirtynytToisestaValintatapajonosta)

              debug(s"Tilankuvaukset ${uusiHakemus.tilanKuvaukset}")

              uusiHakemus.tilanKuvaukset must matchA[Tilankuvaus]
                .EN(vanhaHakemus.tilanKuvaukset.EN)
                .FI(vanhaHakemus.tilanKuvaukset.FI)
                .SV(vanhaHakemus.tilanKuvaukset.SV)

              uusiHakemus.pistetiedot.size mustEqual vanhaHakemus.pistetiedot.size
              uusiHakemus.pistetiedot.foreach(uusiPistetieto => {
                val vanhaPistetieto = vanhaHakemus.pistetiedot.find(_.tunniste.equals(uusiPistetieto.tunniste)).get
                debug(s"Pistetieto ${uusiPistetieto.tunniste}")
                uusiPistetieto must matchA[Pistetieto]
                  .tunniste(vanhaPistetieto.tunniste)
                  .arvo(vanhaPistetieto.arvo)
                  .laskennallinenArvo(vanhaPistetieto.laskennallinenArvo)
                  .osallistuminen(vanhaPistetieto.osallistuminen)
                  .tyypinKoodiUri(vanhaPistetieto.tyypinKoodiUri)
                  .tilastoidaan(vanhaPistetieto.tilastoidaan)
              })

              debug(s"Tilahistoria ${uusiHakemus.tilaHistoria}")
              if (uusiHakemus.tilaHistoria.size > vanhaHakemus.tilaHistoria.size) {
                debug(s"vanhaHakemus.tilaHistoria: ${vanhaHakemus.hakemusOid} / ${vanhaHakemus.valintatapajonoOid} : ${vanhaHakemus.tilaHistoria}")
                debug(s"uusiHakemus.tilaHistoria: ${uusiHakemus.hakemusOid} / ${uusiHakemus.valintatapajonoOid} : ${uusiHakemus.tilaHistoria}")
              }
              uusiHakemus.tilaHistoria.size must be_<=(vanhaHakemus.tilaHistoria.size)
              for ((uusiTilahistoria, i) <- uusiHakemus.tilaHistoria.reverse.zipWithIndex) {
                val vanhaTilahistoria = vanhaHakemus.tilaHistoria.reverse(i)
                uusiTilahistoria must matchA[Tilahistoria]
                  .tila(vanhaTilahistoria.tila)
                  .luotu(vanhaTilahistoria.luotu)
              }

            })
          })
          if (uusiHakukohde.hakijaryhmat.size != vanhaHakukohde.hakijaryhmat.size) {
            debug("uusiHakukohde.hakijaryhmat:")
            uusiHakukohde.hakijaryhmat.foreach { h => debug(s"\t$h") }
            debug("vanhaHakukohde.hakijaryhmat:")
            vanhaHakukohde.hakijaryhmat.foreach { h => debug(s"\t$h") }
          }
          uusiHakukohde.hakijaryhmat.size mustEqual vanhaHakukohde.hakijaryhmat.size
          hakijaryhmat = hakijaryhmat + uusiHakukohde.hakijaryhmat.size
          uusiHakukohde.hakijaryhmat.foreach(uusiHakijaryhma => {
            debug(s"Hakijaryhma ${uusiHakijaryhma.oid}")
            val vanhaHakijaryhma = vanhaHakukohde.hakijaryhmat.find(_.oid.equals(uusiHakijaryhma.oid)).get
            uusiHakijaryhma must matchA[Hakijaryhma]
              .prioriteetti(vanhaHakijaryhma.prioriteetti)
              .paikat(vanhaHakijaryhma.paikat)
              .nimi(vanhaHakijaryhma.nimi)
              .hakukohdeOid(vanhaHakijaryhma.hakukohdeOid)
              .kiintio(vanhaHakijaryhma.kiintio)
              .kaytaKaikki(vanhaHakijaryhma.kaytaKaikki)
              .tarkkaKiintio(vanhaHakijaryhma.tarkkaKiintio)
              .kaytetaanRyhmaanKuuluvia(vanhaHakijaryhma.kaytetaanRyhmaanKuuluvia)
              .hakijaryhmatyyppikoodiUri(vanhaHakijaryhma.hakijaryhmatyyppikoodiUri)
              .valintatapajonoOid(vanhaHakijaryhma.valintatapajonoOid)
            uusiHakijaryhma.hakemusOid.size mustEqual vanhaHakijaryhma.hakemusOid.size
            uusiHakijaryhma.hakemusOid.diff(vanhaHakijaryhma.hakemusOid) mustEqual List()
          })
        })

        info(s"Valintatapajonoja ${valintatapajonot}")
        info(s"Hakemuksia ${hakemukset}")
        info(s"Hakijaryhmiä ${hakijaryhmat}")

      }
      true must beTrue
    }
  }

  private def createVanhaSijoitteluajo(hakuOid: String) = {
    val sijoitteluajo = get[Sijoitteluajo](() => getSijoitteluajo(hakuOid))
    sijoitteluajo.copy(hakukohteet = sijoitteluajo.hakukohteet.map(h => get[Hakukohde](getHakukohde(hakuOid, h.oid))))
  }

  private def get[T](fetch:() => String)(implicit m: Manifest[T]): T = parse(fetch()).extract[T]

  private def getSijoitteluajo(hakuOid: String): String = getOld(s"$oldHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest")
  private def getHakukohde(hakuOid: String, hakukohdeOid: String) = () => getOld(s"$oldHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest/hakukohde/$hakukohdeOid")

  private def getNewSijoittelu(hakuOid: String, vtsSessionCookie: String) = {
    val url = newHost + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest"
    val (statusCode, responseHeaders, result) = time("Uuden sijoittelun haku") {
      new DefaultHttpRequest(Http(url)
        .method("GET")
        .options(Seq(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(120000)))
        .header("Content-Type", "application/json")
        .header("Cookie", s"session=$vtsSessionCookie")
      ).responseWithHeaders() }
    if (statusCode != 200) {
      throw new RuntimeException(s"Got status $statusCode from $url with headers $responseHeaders and body $result")
    }
    fileForStoringNewResponse.foreach { f =>
      info(s"Tallennetaan uuden APIn vastaus (${result.size} tavua) tiedostoon $f")
      FileUtils.writeStringToFile(new File(f), result)
    }
    result
  }


  private def getOld(uriString:String) = {
    vanhaSijoitteluCasClient.prepare(Request(method = Method.GET, uri = createUri(uriString))).flatMap {
      case r if 200 == r.status.code => r.as[String]
      case r => Task.fail(new RuntimeException(r.toString))
    }.run
  }

  private def createUri(uriString:String): Uri = Uri.fromString(uriString).getOrElse(throw new RuntimeException(s"Invalid uri"))

  private def createCasClient(target:String): Client = {
    val casParams = CasParams(target, cas_user, cas_password)
    new CasAuthenticatingClient(
      new CasClient(cas_url, org.http4s.client.blaze.defaultClient),
      casParams,
      org.http4s.client.blaze.defaultClient,
      null
    )
  }


}
case class Sijoitteluajo(sijoitteluajoId:Long, hakuOid:String, startMils:Long, endMils:Long, hakukohteet:List[Hakukohde])

case class Hakukohde(sijoitteluajoId:Option[Long], oid:String, tila:Option[String], tarjoajaOid:Option[String],
                     kaikkiJonotSijoiteltu:Boolean, ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet:Option[Long],
                     valintatapajonot:List[Valintatapajono], hakijaryhmat:List[Hakijaryhma])

case class Hakijaryhma(prioriteetti:Option[Long], paikat:Option[Long], oid:String, nimi:Option[String],
                       hakukohdeOid:Option[String], kiintio:Option[Long],
                       kaytaKaikki:Boolean, tarkkaKiintio:Boolean, kaytetaanRyhmaanKuuluvia:Boolean,
                       hakijaryhmatyyppikoodiUri:Option[String], valintatapajonoOid:Option[String], hakemusOid:List[String])

case class Valintatapajono(tasasijasaanto:Option[String], tila:Option[String], oid:String,
                           prioriteetti:Option[Long], aloituspaikat:Option[Long], alkuperaisetAloituspaikat:Option[Long], alinHyvaksyttyPistemaara:Option[Long],
                           eiVarasijatayttoa:Boolean, kaikkiEhdonTayttavatHyvaksytaan:Boolean, poissaOlevaTaytto:Boolean, valintaesitysHyvaksytty:Option[Boolean],
                           hakeneet:Option[Long], hyvaksytty:Option[Long], varalla:Option[Long], varasijat:Option[Long], hakemukset:List[Hakemus],
                           varasijaTayttoPaivat:Option[java.util.Date], varasijojaTaytetaanAsti:Option[java.util.Date], tayttojono:Option[String])

case class Hakemus(hakijaOid:Option[String], hakemusOid:String, pisteet:Option[Long], paasyJaSoveltuvuusKokeenTulos:Option[Long],
                   etunimi:Option[String], sukunimi:Option[String], prioriteetti:Option[Long], jonosija:Option[Long], tasasijaJonosija:Option[Long],
                   tila:Option[String], hyvaksyttyHarkinnanvaraisesti:Boolean, varasijanNumero:Option[Long], sijoitteluajoId:Option[Long],
                   hakukohdeOid:Option[String], tarjoajaOid:Option[String], valintatapajonoOid:Option[String],
                   hakuOid:Option[String], onkoMuuttunutViimeSijoittelussa:Boolean, siirtynytToisestaValintatapajonosta:Boolean,
                   tilanKuvaukset:Tilankuvaus, pistetiedot:List[Pistetieto], tilaHistoria:List[Tilahistoria])

case class Pistetieto(tunniste:String, arvo:String, laskennallinenArvo:String, osallistuminen:String, tyypinKoodiUri:String, tilastoidaan:Boolean)

case class Tilankuvaus(SV:Option[String], FI:Option[String], EN:Option[String])

case class Tilahistoria(tila:String, luotu:Long)
