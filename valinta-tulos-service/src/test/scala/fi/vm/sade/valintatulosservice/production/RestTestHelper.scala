package fi.vm.sade.valintatulosservice.production

import fi.vm.sade.utils.cas.{CasAuthenticatingClient, CasClient, CasParams}
import fi.vm.sade.utils.http.DefaultHttpRequest
import org.http4s.client.Client
import org.http4s.{Method, Request, Uri}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse

import scalaj.http.{Http, HttpOptions}
import scalaz.concurrent.Task

trait RestTestHelper {
  val casUserNew = System.getProperty("cas_user_new")
  val casPasswordNew = System.getProperty("cas_password_new")
  val casUserOld = System.getProperty("cas_user_old")
  val casPasswordOld = System.getProperty("cas_password_old")
  def casUrlOld:String

  lazy val vanhaSijoitteluCasClient = createCasClientForOldData("/sijoittelu-service")

  implicit val formats = DefaultFormats

  protected def getOld(uriString: String): String = {
    vanhaSijoitteluCasClient.fetch(Request(method = Method.GET, uri = createUri(uriString))) {
      case r if 200 == r.status.code => r.as[String]
      case r => Task.fail(new RuntimeException(s"$uriString => ${r.toString}"))
    }.run
  }

  protected def createUri(uriString:String): Uri = Uri.fromString(uriString).getOrElse(throw new RuntimeException(s"Invalid uri"))

  protected def createCasClientForOldData(target:String): Client = {
    val casParams = CasParams(target, casUserOld, casPasswordOld)
    CasAuthenticatingClient(
      casClient = new CasClient(casUrlOld, org.http4s.client.blaze.defaultClient),
      casParams = casParams,
      serviceClient = org.http4s.client.blaze.defaultClient,
      clientCallerId = "RestTestHelper",
      sessionCookieName = "JSESSIONID"
    )
  }

  protected def get[T](fetch:() => String)(implicit m: Manifest[T]): T = parse(fetch()).extract[T]

  protected def getNew(url: String, vtsSessionCookie: String): String = {
    val (statusCode, responseHeaders, result) =
      new DefaultHttpRequest(Http(url)
        .method("GET")
        .options(Seq(HttpOptions.connTimeout(10000), HttpOptions.readTimeout(120000)))
        .header("Content-Type", "application/json")
        .header("Cookie", s"session=$vtsSessionCookie")
      ).responseWithHeaders()
    if (statusCode != 200) {
      throw new RuntimeException(s"Got status $statusCode from $url with headers $responseHeaders and body $result")
    }
    result
  }
}

case class Sijoitteluajo(sijoitteluajoId:Long, hakuOid:String, startMils:Long, endMils:Long, hakukohteet:List[Hakukohde])

case class Hakukohde(sijoitteluajoId:Option[Long], oid:String, tila:Option[String], tarjoajaOid:Option[String],
                     kaikkiJonotSijoiteltu:Boolean, ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet:Option[Long],
                     valintatapajonot:List[Valintatapajono], hakijaryhmat:List[Hakijaryhma])

case class Hakijaryhma(prioriteetti:Option[Long], paikat:Option[Long], oid:String, nimi:Option[String],
                       hakukohdeOid:Option[String], kiintio:Option[Long],
                       kaytaKaikki:Option[Boolean], tarkkaKiintio:Option[Boolean], kaytetaanRyhmaanKuuluvia:Option[Boolean],
                       hakijaryhmatyyppikoodiUri:Option[String], valintatapajonoOid:Option[String], hakemusOid:List[String])

case class Valintatapajono(tasasijasaanto:Option[String], tila:Option[String], oid:String,
                           prioriteetti:Option[Long], aloituspaikat:Option[Long], alkuperaisetAloituspaikat:Option[Long], alinHyvaksyttyPistemaara:Option[Long],
                           eiVarasijatayttoa:Option[Boolean], kaikkiEhdonTayttavatHyvaksytaan:Option[Boolean], poissaOlevaTaytto:Option[Boolean], valintaesitysHyvaksytty:Option[Boolean],
                           hakeneet:Option[Long], hyvaksytty:Option[Long], varalla:Option[Long], varasijat:Option[Long], hakemukset:List[Hakemus],
                           varasijaTayttoPaivat:Option[java.util.Date], varasijojaTaytetaanAsti:Option[java.util.Date], tayttojono:Option[String])

case class Hakemus(hakijaOid:Option[String], hakemusOid:String, pisteet:Option[Long], paasyJaSoveltuvuusKokeenTulos:Option[Long],
                   prioriteetti:Option[Long], jonosija:Option[Long], tasasijaJonosija:Option[Long],
                   tila:Option[String], hyvaksyttyHarkinnanvaraisesti:Option[Boolean], varasijanNumero:Option[Long], sijoitteluajoId:Option[Long],
                   hakukohdeOid:Option[String], tarjoajaOid:Option[String], valintatapajonoOid:Option[String],
                   hakuOid:Option[String], onkoMuuttunutViimeSijoittelussa:Option[Boolean], siirtynytToisestaValintatapajonosta:Option[Boolean],
                   tilanKuvaukset:Tilankuvaus, pistetiedot:List[Pistetieto], tilaHistoria:List[Tilahistoria])

case class Pistetieto(tunniste:String, arvo:String, laskennallinenArvo:String, osallistuminen:String, tyypinKoodiUri:Option[String], tilastoidaan:Option[Boolean])

case class Tilankuvaus(SV:Option[String], FI:Option[String], EN:Option[String])

case class Tilahistoria(tila:String, luotu:Long)

case class Hakija(hakijaOid:String, hakemusOid:String, hakutoiveet:List[Hakutoive])

case class Hakutoive(hakutoive:Int, hakukohdeOid:String, tarjoajaOid:Option[String], pistetiedot:List[Pistetieto], hakijaryhmat:List[Hakijaryhma],
                     kaikkiJonotSijoiteltu:Option[Boolean], ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet:Option[String],
                     vastaanottotieto:Option[String], hakutoiveenValintatapajonot:List[HakutoiveenValintatapajono])

case class HakutoiveenValintatapajono(valintatapajonoOid:String, valintatapajonoNimi:String, eiVarasijatayttoa:Option[Boolean],
                                      jonosija:Option[Int], paasyJaSoveltuvuusKokeenTulos:Option[String], varasijanNumero:Option[Int],
                                      tila:Option[String], tilanKuvaukset:Tilankuvaus, ilmoittautumisTila:Option[String],
                                      hyvaksyttyHarkinnanvaraisesti:Option[Boolean], tasasijaJonosija:Option[Int], pisteet:Option[String],
                                      alinHyvaksyttyPistemaara:Option[String], hakeneet:Option[Int], hyvaksytty:Option[Int], varalla:Option[Int],
                                      varasijat:Option[Int], varasijaTayttoPaivat:Option[Int], varasijojaKaytetaanAlkaen:Option[java.util.Date],
                                      varasijojaTaytetaanAsti:Option[java.util.Date], valintatuloksenViimeisinMuutos:Option[java.util.Date],
                                      hakemuksenTilanViimeisinMuutos:Option[java.util.Date], tayttojono:Option[String], julkaistavissa:Option[Boolean],
                                      ehdollisestiHyvaksyttavissa:Option[Boolean], hyvaksyttyVarasijalta:Option[Boolean])
