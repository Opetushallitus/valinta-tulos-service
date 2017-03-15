package fi.vm.sade.valintatulosservice.production

import fi.vm.sade.utils.cas.VtsAuthenticatingClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.language.experimental.macros

@Ignore
@RunWith(classOf[JUnitRunner])
class SijoittelunOsatRestTest extends Specification with MatcherMacros with Logging with PerformanceLogger with RestTestHelper {
  val host = "https://testi.virkailija.opintopolku.fi"
  //val cas_user = System.getProperty("cas_user")
  //val cas_password = System.getProperty("cas_password")
  override val cas_url = host + "/cas"

  val vtsClient = new VtsAuthenticatingClient(host, "/valinta-tulos-service", cas_user, cas_password)
  val vtsSessionCookie = vtsClient.getVtsSession(host)

  val hakuOid = "1.2.246.562.29.75203638285"
  val hakemusOid = "1.2.246.562.11.00006979630"

  "Hakemus in new sijoittelu should equal to hakemus in old sijoittelu" in {
    val uusiHakemus = time("Get uusi hakemus") { get[Hakija](() => getNewHakemus(hakuOid, hakemusOid, vtsSessionCookie))}
    val vanhaHakemus = time("Get vanha hakemus") { get[Hakija](() => getOld(s"$host/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest/hakemus/$hakemusOid")) }

    uusiHakemus.etunimi mustEqual vanhaHakemus.etunimi
    uusiHakemus.sukunimi mustEqual vanhaHakemus.sukunimi
    uusiHakemus.hakemusOid mustEqual vanhaHakemus.hakemusOid
    uusiHakemus.hakijaOid mustEqual vanhaHakemus.hakijaOid
    uusiHakemus.hakutoiveet.size mustEqual vanhaHakemus.hakutoiveet.size

    logger.info(s"Hakemuksien lukumäärä ${uusiHakemus.hakutoiveet.size}")
    uusiHakemus.hakutoiveet.foreach(uusiHakutoive => {
      val vanhaHakutoive = vanhaHakemus.hakutoiveet.find(_.hakukohdeOid == uusiHakutoive.hakukohdeOid).get

      uusiHakutoive.hakutoive mustEqual vanhaHakutoive.hakutoive
      uusiHakutoive.tarjoajaOid mustEqual vanhaHakutoive.tarjoajaOid
      vanhaHakutoive.vastaanottotieto match {
        case None => uusiHakutoive.vastaanottotieto mustEqual Some("KESKEN")
        case x => uusiHakutoive.vastaanottotieto mustEqual x
      }
      uusiHakutoive.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual vanhaHakutoive.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet
      uusiHakutoive.kaikkiJonotSijoiteltu mustEqual vanhaHakutoive.kaikkiJonotSijoiteltu

      uusiHakutoive.pistetiedot.size mustEqual vanhaHakutoive.pistetiedot.size
      logger.info(s"Pistetietojen lukumäärä ${uusiHakutoive.pistetiedot.size}")
      uusiHakutoive.pistetiedot.foreach(uusiPistetieto => {
        val vanhaPistetieto = vanhaHakutoive.pistetiedot.find(_.tunniste.equals(uusiPistetieto.tunniste)).get
        debug(s"Pistetieto ${uusiPistetieto.tunniste}")
        uusiPistetieto must matchA[Pistetieto]
          .tunniste(vanhaPistetieto.tunniste)
          .arvo(vanhaPistetieto.arvo)
          .laskennallinenArvo(vanhaPistetieto.laskennallinenArvo)
          .osallistuminen(vanhaPistetieto.osallistuminen)
          .tyypinKoodiUri(vanhaPistetieto.tyypinKoodiUri)
          .tilastoidaan(vanhaPistetieto.tilastoidaan)
      })

      uusiHakutoive.hakijaryhmat.size mustEqual vanhaHakutoive.hakijaryhmat.size
      logger.info(s"Hakijaryhmien lukumäärä ${uusiHakutoive.hakijaryhmat.size}")
      uusiHakutoive.hakijaryhmat.foreach(uusiHakijaryhma => {
        val vanhaHakijaryhma = vanhaHakutoive.hakijaryhmat.find(_.oid.equals(uusiHakijaryhma.oid)).get
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

      uusiHakutoive.hakutoiveenValintatapajonot.size mustEqual vanhaHakutoive.hakutoiveenValintatapajonot.size
      logger.info(s"Hakutoiveen valintatapajonojen lukumäärä ${uusiHakutoive.hakutoiveenValintatapajonot.size}")
      uusiHakutoive.hakutoiveenValintatapajonot.foreach(uusiValintatapajono => {
        val vanhaValintatapajono = vanhaHakutoive.hakutoiveenValintatapajonot.find(_.valintatapajonoOid == uusiValintatapajono.valintatapajonoOid).get
        uusiValintatapajono.valintatapajonoNimi mustEqual vanhaValintatapajono.valintatapajonoNimi
        uusiValintatapajono.jonosija mustEqual vanhaValintatapajono.jonosija
        uusiValintatapajono.eiVarasijatayttoa mustEqual vanhaValintatapajono.eiVarasijatayttoa
        uusiValintatapajono.paasyJaSoveltuvuusKokeenTulos mustEqual vanhaValintatapajono.paasyJaSoveltuvuusKokeenTulos
        uusiValintatapajono.varasijanNumero mustEqual vanhaValintatapajono.varasijanNumero
        uusiValintatapajono.tila mustEqual vanhaValintatapajono.tila
        uusiValintatapajono.tilanKuvaukset mustEqual vanhaValintatapajono.tilanKuvaukset
        uusiValintatapajono.ilmoittautumisTila mustEqual vanhaValintatapajono.ilmoittautumisTila
        uusiValintatapajono.hyvaksyttyHarkinnanvaraisesti mustEqual vanhaValintatapajono.hyvaksyttyHarkinnanvaraisesti
        uusiValintatapajono.tasasijaJonosija mustEqual vanhaValintatapajono.tasasijaJonosija
        uusiValintatapajono.pisteet mustEqual vanhaValintatapajono.pisteet
        uusiValintatapajono.alinHyvaksyttyPistemaara mustEqual vanhaValintatapajono.alinHyvaksyttyPistemaara
        uusiValintatapajono.hakeneet mustEqual vanhaValintatapajono.hakeneet
        uusiValintatapajono.hyvaksytty mustEqual vanhaValintatapajono.hyvaksytty
        uusiValintatapajono.varalla mustEqual vanhaValintatapajono.varalla
        uusiValintatapajono.varasijat mustEqual vanhaValintatapajono.varasijat
        uusiValintatapajono.varasijaTayttoPaivat mustEqual vanhaValintatapajono.varasijaTayttoPaivat
        uusiValintatapajono.varasijojaKaytetaanAlkaen mustEqual vanhaValintatapajono.varasijojaKaytetaanAlkaen
        uusiValintatapajono.valintatuloksenViimeisinMuutos mustEqual vanhaValintatapajono.valintatuloksenViimeisinMuutos
        uusiValintatapajono.varasijojaTaytetaanAsti mustEqual vanhaValintatapajono.varasijojaTaytetaanAsti
        uusiValintatapajono.hakemuksenTilanViimeisinMuutos mustEqual vanhaValintatapajono.hakemuksenTilanViimeisinMuutos
        uusiValintatapajono.tayttojono mustEqual vanhaValintatapajono.tayttojono
        uusiValintatapajono.julkaistavissa mustEqual vanhaValintatapajono.julkaistavissa
        uusiValintatapajono.ehdollisestiHyvaksyttavissa mustEqual vanhaValintatapajono.ehdollisestiHyvaksyttavissa
        uusiValintatapajono.hyvaksyttyVarasijalta mustEqual vanhaValintatapajono.hyvaksyttyVarasijalta
      })

    })
    true mustEqual true
  }

  override def getOld(uriString:String) = {
    val result = super.getOld(uriString)
    println(result)
    result
  }

  private def getNewHakemus(hakuOid: String, hakemusOid:String, vtsSessionCookie: String) = {
    val url = host + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest/hakemus/$hakemusOid"
    val result = time("Uuden hakemuksen haku") {getNew(url, vtsSessionCookie)}
    println(result)
    result
  }
}