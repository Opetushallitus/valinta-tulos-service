package fi.vm.sade.valintatulosservice.production

import java.io.File

import fi.vm.sade.utils.cas._
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.apache.commons.io.FileUtils
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.language.experimental.macros

@Ignore
@RunWith(classOf[JUnitRunner])
class SijoitteluRestTest extends Specification with MatcherMacros with Logging with PerformanceLogger with RestTestHelper {
  val oldHost = "https://testi.virkailija.opintopolku.fi"
  //private val newHost = "https://testi.virkailija.opintopolku.fi"
  private val newHost = "http://localhost:8097"

  //val cas_user = System.getProperty("cas_user")
  //val cas_password = System.getProperty("cas_password")
  override val cas_url = oldHost + "/cas"
  //val haku_oid = "1.2.246.562.29.75203638285" // Kevään 2016 kk-yhteishaku
  //val haku_oid = "1.2.246.562.29.14662042044" // Kevään 2016 2. asteen yhteishaku
  //val haku_oid = "1.2.246.562.29.95390561488" // Kevään 2015 kk-yhteishaku
  //val haku_oid = "1.2.246.562.29.28924613947" // Haku ammatilliseen opettajankoulutukseen 2017
//  val haku_oid = "1.2.246.562.29.87593180141" // Syksyn 2016 kk-yhteishaku
  val hakuOidsToTest = Seq("1.2.246.562.29.669559278110","1.2.246.562.29.28924613947")

  val infoOn = true
  val debugOn = false
  val fileForStoringNewResponse: Option[String] = /*None  //  */ Some("/tmp/lol.json")

  def info(message:String) = if(infoOn) logger.info(message)
  def debug(message:String) = if(debugOn) logger.info(message)

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
        compareFields(uusiSijoittelu.sijoitteluajoId, vanhaSijoittelu.sijoitteluajoId, "sijoittelu.sijoitteluajoId")
        compareFields(uusiSijoittelu.hakuOid, vanhaSijoittelu.hakuOid, "sijoittelu.hakuOid")
        compareFields(uusiSijoittelu.startMils, vanhaSijoittelu.startMils, "sijoittelu.startMills")
        compareFields(uusiSijoittelu.endMils, vanhaSijoittelu.endMils, "sijoittelu.endMills")
        compareFields(uusiSijoittelu.hakukohteet.size, vanhaSijoittelu.hakukohteet.size, "hakukohteet.size")

        var valintatapajonot = 0
        var hakemukset = 0
        var hakijaryhmat = 0

        info(s"Hakukohteita ${uusiSijoittelu.hakukohteet.size}")
        uusiSijoittelu.hakukohteet.foreach(uusiHakukohde => {
          debug(s"Hakukohde ${uusiHakukohde.oid}")
          val vanhaHakukohde = vanhaSijoittelu.hakukohteet.find(_.oid.equals(uusiHakukohde.oid)).get
          compareFields(uusiHakukohde.sijoitteluajoId, vanhaHakukohde.sijoitteluajoId, "hakukohteen.sijoitteluajoId")
          compareFields(uusiHakukohde.tila, vanhaHakukohde.tila, "hakukohde.tila")
          compareFields(uusiHakukohde.tarjoajaOid, None, "hakukohde.tarjoajaOid") // tarjoaja oids are only fetched from tarjonta on demand these days
          compareFields(uusiHakukohde.kaikkiJonotSijoiteltu, vanhaHakukohde.kaikkiJonotSijoiteltu, "hakukohde.kaikkiJonotSijoiteltu")
          compareFields(uusiHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet,
            vanhaHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet, "hakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet")

          compareFields(uusiHakukohde.valintatapajonot.size, vanhaHakukohde.valintatapajonot.size, "hakukohde.valintatapajonot.size")
          valintatapajonot = valintatapajonot + uusiHakukohde.valintatapajonot.size
          uusiHakukohde.valintatapajonot.foreach(uusiValintatapajono => {
            debug(s"Valintatapajono ${uusiValintatapajono.oid}")
            val vanhaValintatapajono = vanhaHakukohde.valintatapajonot.find(_.oid.equals(uusiValintatapajono.oid)).get
            compareFields(uusiValintatapajono.tasasijasaanto, vanhaValintatapajono.tasasijasaanto, "valintatapajono.tasasijasaanto")
            compareFields(uusiValintatapajono.tila, vanhaValintatapajono.tila, "valintatapajono.tila")
            compareFields(uusiValintatapajono.prioriteetti, vanhaValintatapajono.prioriteetti, "valintatapajono.prioriteetti")
            compareFields(uusiValintatapajono.aloituspaikat, vanhaValintatapajono.aloituspaikat, "valintatapajono.aloituspaikat")
            compareFields(uusiValintatapajono.alkuperaisetAloituspaikat, vanhaValintatapajono.alkuperaisetAloituspaikat, "valintatapajono.alkuperaisetAloituspaikat")
            compareFields(uusiValintatapajono.alinHyvaksyttyPistemaara, vanhaValintatapajono.alinHyvaksyttyPistemaara, "valintatapajono.alinHyvaksyttyPistemaara")
            compareFields(uusiValintatapajono.eiVarasijatayttoa, vanhaValintatapajono.eiVarasijatayttoa, "valintatapajono.eiVarasijatayttoa")
            compareFields(uusiValintatapajono.kaikkiEhdonTayttavatHyvaksytaan, vanhaValintatapajono.kaikkiEhdonTayttavatHyvaksytaan, "valintatapajono.kaikkiEhdonTayttavatHyvaksytaan")
            compareFields(uusiValintatapajono.poissaOlevaTaytto, vanhaValintatapajono.poissaOlevaTaytto, "valintatapajono.poissaOlevaTaytto")
            compareFields(uusiValintatapajono.hakeneet, vanhaValintatapajono.hakeneet, "valintatapajono.hakeneet")
            compareFields(uusiValintatapajono.hyvaksytty, vanhaValintatapajono.hyvaksytty, "valintatapajono.hyvaksytty")
            compareFields(uusiValintatapajono.varalla, vanhaValintatapajono.varalla, "valintatapajono.varalla")
            compareFields(uusiValintatapajono.varasijat, vanhaValintatapajono.varasijat, "valintatapajono.varasijat")
            compareFields(uusiValintatapajono.varasijaTayttoPaivat, vanhaValintatapajono.varasijaTayttoPaivat, "valintatapajono.varasijaTayttoPaivat")
            compareFields(uusiValintatapajono.varasijojaTaytetaanAsti, vanhaValintatapajono.varasijojaTaytetaanAsti, "valintatapajono.varasijojaTaytetaanAsti")
            compareFields(uusiValintatapajono.tayttojono, vanhaValintatapajono.tayttojono, "valintatapajono.tayttojono")

            compareFields(uusiValintatapajono.valintaesitysHyvaksytty, vanhaValintatapajono.valintaesitysHyvaksytty, "valintatapajono.valintaesitysHyvaksytty")

            compareFields(uusiValintatapajono.hakemukset.size, vanhaValintatapajono.hakemukset.size, "valintatapajono.hakemukset.size")
            hakemukset = hakemukset + uusiValintatapajono.hakemukset.size
            uusiValintatapajono.hakemukset.foreach(uusiHakemus => {
              debug(s"Hakemus ${uusiHakemus.hakemusOid}")
              val vanhaHakemus = vanhaValintatapajono.hakemukset.find(_.hakemusOid.equals(uusiHakemus.hakemusOid)).get
              compareFields(uusiHakemus.hakijaOid, vanhaHakemus.hakijaOid, "hakemus.hakijaOid")
              compareFields(uusiHakemus.pisteet, vanhaHakemus.pisteet, "hakemus.pisteet")
              compareFields(uusiHakemus.paasyJaSoveltuvuusKokeenTulos, vanhaHakemus.paasyJaSoveltuvuusKokeenTulos, "hakemus.paasyJaSoveltuvuusKokeenTulos")
              compareFields(uusiHakemus.etunimi, vanhaHakemus.etunimi, "hakemus.etunimi")
              compareFields(uusiHakemus.sukunimi, vanhaHakemus.sukunimi, "hakemus.sukunimi")
              compareFields(uusiHakemus.prioriteetti, vanhaHakemus.prioriteetti, "hakemus.prioriteetti")
              compareFields(uusiHakemus.jonosija, vanhaHakemus.jonosija, "hakemus.jonosija")
              compareFields(uusiHakemus.tasasijaJonosija, vanhaHakemus.tasasijaJonosija, "hakemus.tasasijaJonosija")
              compareFields(uusiHakemus.tila, vanhaHakemus.tila, "hakemus.tila")
              compareFields(uusiHakemus.hyvaksyttyHarkinnanvaraisesti, vanhaHakemus.hyvaksyttyHarkinnanvaraisesti, "hakemus.hyvaksyttyHarkinnanvaraisesti")
              compareFields(uusiHakemus.varasijanNumero, vanhaHakemus.varasijanNumero, "hakemus.varasijanNumero")
              compareFields(uusiHakemus.valintatapajonoOid, vanhaHakemus.valintatapajonoOid, "hakemus.valintatapajonoOid")
              compareFields(uusiHakemus.hakuOid, vanhaHakemus.hakuOid, "hakemus.hakuOid")
              compareFields(uusiHakemus.onkoMuuttunutViimeSijoittelussa, vanhaHakemus.onkoMuuttunutViimeSijoittelussa, "hakemus.onkoMuuttunutViimeSijoittelussa")
              compareFields(uusiHakemus.siirtynytToisestaValintatapajonosta, vanhaHakemus.siirtynytToisestaValintatapajonosta, "hakemus.siirtynytToisestaValintatapajonosta")

              debug(s"Tilankuvaukset ${uusiHakemus.tilanKuvaukset}")

              compareFields(uusiHakemus.tilanKuvaukset.EN, vanhaHakemus.tilanKuvaukset.EN, "hakemus.tilankuvaukset.EN")
              compareFields(uusiHakemus.tilanKuvaukset.FI, vanhaHakemus.tilanKuvaukset.FI, "hakemus.tilankuvaukset.FI")
              compareFields(uusiHakemus.tilanKuvaukset.SV, vanhaHakemus.tilanKuvaukset.SV, "hakemus.tilankuvaukset.SV")

              compareFields(uusiHakemus.pistetiedot.size, vanhaHakemus.pistetiedot.size, "hakemus.pistetiedot.size")
              uusiHakemus.pistetiedot.foreach(uusiPistetieto => {
                val vanhaPistetieto = vanhaHakemus.pistetiedot.find(_.tunniste.equals(uusiPistetieto.tunniste)).get
                debug(s"Pistetieto ${uusiPistetieto.tunniste}")
                compareFields(uusiPistetieto.tunniste, vanhaPistetieto.tunniste, "pistetieto.tunniste")
                compareFields(uusiPistetieto.arvo, vanhaPistetieto.arvo, "pistetieto.arvo")
                compareFields(uusiPistetieto.laskennallinenArvo, vanhaPistetieto.laskennallinenArvo, "pistetieto.laskennallinenArvo")
                compareFields(uusiPistetieto.osallistuminen, vanhaPistetieto.osallistuminen, "pistetieto.osallistuminen")
                compareFields(uusiPistetieto.tyypinKoodiUri, vanhaPistetieto.tyypinKoodiUri, "pistetieto.tyypinKoodiUri")
                compareFields(uusiPistetieto.tilastoidaan, vanhaPistetieto.tilastoidaan, "pistetieto.tilastoidaan")
              })

              debug(s"Tilahistoria ${uusiHakemus.tilaHistoria}")
              if (uusiHakemus.tilaHistoria.size > vanhaHakemus.tilaHistoria.size) {
                debug(s"vanhaHakemus.tilaHistoria: ${vanhaHakemus.hakemusOid} / ${vanhaHakemus.valintatapajonoOid} : ${vanhaHakemus.tilaHistoria}")
                debug(s"uusiHakemus.tilaHistoria: ${uusiHakemus.hakemusOid} / ${uusiHakemus.valintatapajonoOid} : ${uusiHakemus.tilaHistoria}")
              }
              uusiHakemus.tilaHistoria.size must be_<=(vanhaHakemus.tilaHistoria.size)
              for ((uusiTilahistoria, i) <- uusiHakemus.tilaHistoria.reverse.zipWithIndex) {
                val vanhaTilahistoria = vanhaHakemus.tilaHistoria.reverse(i)
                compareFields(uusiTilahistoria.tila, vanhaTilahistoria.tila, "tilahistoria.tila")
                compareFields(uusiTilahistoria.luotu, vanhaTilahistoria.luotu, "tilahistoria.luotu")
              }

            })
          })
          if (uusiHakukohde.hakijaryhmat.size != vanhaHakukohde.hakijaryhmat.size) {
            debug("uusiHakukohde.hakijaryhmat:")
            uusiHakukohde.hakijaryhmat.foreach { h => debug(s"\t$h") }
            debug("vanhaHakukohde.hakijaryhmat:")
            vanhaHakukohde.hakijaryhmat.foreach { h => debug(s"\t$h") }
          }
          compareFields(uusiHakukohde.hakijaryhmat.size, vanhaHakukohde.hakijaryhmat.size, "hakijaryhmat.size")
          hakijaryhmat = hakijaryhmat + uusiHakukohde.hakijaryhmat.size
          uusiHakukohde.hakijaryhmat.foreach(uusiHakijaryhma => {
            debug(s"Hakijaryhma ${uusiHakijaryhma.oid}")
            val vanhaHakijaryhma = vanhaHakukohde.hakijaryhmat.find(_.oid.equals(uusiHakijaryhma.oid)).get
            compareFields(uusiHakijaryhma.prioriteetti, vanhaHakijaryhma.prioriteetti, "hakijaryhma.prioriteetti")
            compareFields(uusiHakijaryhma.paikat, vanhaHakijaryhma.paikat, "hakijaryhma.paikat")
            compareFields(uusiHakijaryhma.nimi, vanhaHakijaryhma.nimi, "hakijaryhma.nimi")
            compareFields(uusiHakijaryhma.hakukohdeOid, vanhaHakijaryhma.hakukohdeOid, "hakijaryhma.hakukohdeOid")
            compareFields(uusiHakijaryhma.kiintio, vanhaHakijaryhma.kiintio, "hakijaryhma.kiintio")
            compareFields(uusiHakijaryhma.kaytaKaikki, vanhaHakijaryhma.kaytaKaikki, "hakijaryhma.kaytaKaikki")
            compareFields(uusiHakijaryhma.tarkkaKiintio, vanhaHakijaryhma.tarkkaKiintio, "hakijaryhma.tarkkaKiintio")
            compareFields(uusiHakijaryhma.kaytetaanRyhmaanKuuluvia, vanhaHakijaryhma.kaytetaanRyhmaanKuuluvia, "hakijaryhma.kaytetaanRyhmaanKuuluvia")
            compareFields(uusiHakijaryhma.hakijaryhmatyyppikoodiUri, vanhaHakijaryhma.hakijaryhmatyyppikoodiUri, "hakijaryhma.hakijaryhmatyyppikoodiUri")
            compareFields(uusiHakijaryhma.valintatapajonoOid, vanhaHakijaryhma.valintatapajonoOid, "hakijaryhma.valintatapajonoOid")
            compareFields(uusiHakijaryhma.hakemusOid.size, vanhaHakijaryhma.hakemusOid.size, "hakijaryhma.hakemukset.size")
            compareFields(uusiHakijaryhma.hakemusOid.diff(vanhaHakijaryhma.hakemusOid), List(), "hakijaryhma.hakemukset.diff")
          })
        })

        info(s"Valintatapajonoja ${valintatapajonot}")
        info(s"Hakemuksia ${hakemukset}")
        info(s"Hakijaryhmiä ${hakijaryhmat}")

      }
      true must beTrue
    }
  }

  private def compareFields(expected: Any, actual: Any, fieldName: String) = {
    if (expected != actual) {
      logger.error(s"Mismatch in $fieldName: $expected != $actual")
    }
  }

  private def createVanhaSijoitteluajo(hakuOid: String) = {
    val sijoitteluajo = get[Sijoitteluajo](() => getSijoitteluajo(hakuOid))
    sijoitteluajo.copy(hakukohteet = sijoitteluajo.hakukohteet.map(h => get[Hakukohde](getHakukohde(hakuOid, h.oid))))
  }

  private def getSijoitteluajo(hakuOid: String): String = getOld(s"$oldHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest")
  private def getHakukohde(hakuOid: String, hakukohdeOid: String) = () => getOld(s"$oldHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest/hakukohde/$hakukohdeOid")

  private def getNewSijoittelu(hakuOid: String, vtsSessionCookie: String) = {
    val url = newHost + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest"
    val result = time("Uuden sijoittelun haku") { getNew(url, vtsSessionCookie)}
    fileForStoringNewResponse.foreach { f =>
      info(s"Tallennetaan uuden APIn vastaus (${result.size} tavua) tiedostoon $f")
      FileUtils.writeStringToFile(new File(f), result)
    }
    result
  }
}