package fi.vm.sade.valintatulosservice.production

import fi.vm.sade.security.VtsAuthenticatingClient
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import org.http4s.client.blaze.BlazeClientConfig
import org.junit.Ignore
import org.junit.runner.RunWith
import org.specs2.matcher.MatcherMacros
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.language.experimental.macros

@Ignore
@RunWith(classOf[JUnitRunner])
class SijoittelunOsatRestTest
    extends Specification
    with MatcherMacros
    with Logging
    with PerformanceLogger
    with RestTestHelper {
  val casHost = "https://testi.virkailija.opintopolku.fi"
  val oldSijoitteluHost = casHost
  val vtsHost = "http://localhost:8097"
  //val cas_user = System.getProperty("cas_user")
  //val cas_password = System.getProperty("cas_password")
  override val casUrlOld = casHost + "/cas"

  val vtsClient = new VtsAuthenticatingClient(
    casHost,
    vtsHost + "/valinta-tulos-service",
    "auth/login",
    casUserNew,
    casPasswordNew,
    BlazeClientConfig.defaultConfig,
    "vts-test-caller-id"
  )
  val vtsSessionCookie = vtsClient.getVtsSession(casHost)

  val hakuOid = "1.2.246.562.29.75203638285"
  val hakukohdeOid = "1.2.246.562.20.85377848495"
  val hakemusOids =
    List("1.2.246.562.11.00006979630", "1.2.246.562.11.00006926939", "1.2.246.562.11.00004677086")

  "Hakemus in new sijoittelu should equal to hakemus in old sijoittelu" in {
    hakemusOids.foreach { hakemusOid =>
      logger.info(s"HAKEMUS ${hakemusOid}")

      val uusiHakemus = time("Get uusi hakemus") {
        get[Hakija](() => getNewHakemus(hakuOid, hakemusOid, vtsSessionCookie))
      }
      val vanhaHakemus = time("Get vanha hakemus") {
        get[Hakija](() =>
          getOld(
            s"$oldSijoitteluHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest/hakemus/$hakemusOid"
          )
        )
      }

      uusiHakemus.hakemusOid mustEqual vanhaHakemus.hakemusOid
      uusiHakemus.hakijaOid mustEqual vanhaHakemus.hakijaOid
      uusiHakemus.hakutoiveet.size mustEqual vanhaHakemus.hakutoiveet.size

      logger.info(s"Hakutoiveiden lukumäärä ${uusiHakemus.hakutoiveet.size}")
      uusiHakemus.hakutoiveet.foreach(uusiHakutoive => {
        logger.info(s"Hakutoive ${uusiHakutoive.hakukohdeOid}")
        val vanhaHakutoive =
          vanhaHakemus.hakutoiveet.find(_.hakukohdeOid == uusiHakutoive.hakukohdeOid).get

        uusiHakutoive.hakutoive mustEqual vanhaHakutoive.hakutoive
        //TODO: voidaanko jättää pois? uusiHakutoive.tarjoajaOid mustEqual vanhaHakutoive.tarjoajaOid
        vanhaHakutoive.vastaanottotieto match {
          case None => uusiHakutoive.vastaanottotieto mustEqual Some("KESKEN")
          case x    => uusiHakutoive.vastaanottotieto mustEqual x
        }
        uusiHakutoive.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual vanhaHakutoive.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet
        uusiHakutoive.kaikkiJonotSijoiteltu mustEqual vanhaHakutoive.kaikkiJonotSijoiteltu

        uusiHakutoive.hakutoiveenValintatapajonot.size mustEqual vanhaHakutoive.hakutoiveenValintatapajonot.size
        logger.info(
          s"Hakutoiveen valintatapajonojen lukumäärä ${uusiHakutoive.hakutoiveenValintatapajonot.size}"
        )
        uusiHakutoive.hakutoiveenValintatapajonot.foreach(uusiValintatapajono => {
          logger.info(s"Valintatapajono ${uusiValintatapajono.valintatapajonoOid}")
          val vanhaValintatapajono = vanhaHakutoive.hakutoiveenValintatapajonot
            .find(_.valintatapajonoOid == uusiValintatapajono.valintatapajonoOid)
            .get
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
          //uusiValintatapajono.julkaistavissa mustEqual vanhaValintatapajono.julkaistavissa
          uusiValintatapajono.ehdollisestiHyvaksyttavissa mustEqual vanhaValintatapajono.ehdollisestiHyvaksyttavissa
          uusiValintatapajono.hyvaksyttyVarasijalta mustEqual vanhaValintatapajono.hyvaksyttyVarasijalta
        })

        uusiHakutoive.pistetiedot.size mustEqual vanhaHakutoive.pistetiedot.size
        logger.info(s"Pistetietojen lukumäärä ${uusiHakutoive.pistetiedot.size}")
        uusiHakutoive.pistetiedot.foreach(uusiPistetieto => {
          val vanhaPistetieto =
            vanhaHakutoive.pistetiedot.find(_.tunniste.equals(uusiPistetieto.tunniste)).get
          logger.info(s"Pistetieto ${uusiPistetieto.tunniste}")
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
          val vanhaHakijaryhma =
            vanhaHakutoive.hakijaryhmat.find(_.oid.equals(uusiHakijaryhma.oid)).get
          logger.info(s"Hakijaryhma ${uusiHakijaryhma.oid}")
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
    }
    true mustEqual true
  }

  "New sijoitteluajon perustiedot should equal to old sijoitteluajo" in {
    logger.info(s"SIJOITTELUAJO ${hakuOid}")
    val uusiSijoittelu = time("Get uusi sijoitteluajo") {
      get[Sijoitteluajo](() => getNewSijoitteluajo(hakuOid, vtsSessionCookie))
    }
    val vanhaSijoittelu = time("Get vanha sijoitteluajo") {
      get[Sijoitteluajo](() =>
        getOld(
          s"$oldSijoitteluHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest"
        )
      )
    }

    uusiSijoittelu.sijoitteluajoId mustEqual vanhaSijoittelu.sijoitteluajoId
    uusiSijoittelu.hakuOid mustEqual vanhaSijoittelu.hakuOid
    uusiSijoittelu.startMils mustEqual vanhaSijoittelu.startMils
    uusiSijoittelu.endMils mustEqual vanhaSijoittelu.endMils
    uusiSijoittelu.hakukohteet.size mustEqual vanhaSijoittelu.hakukohteet.size

    logger.info(s"Hakukohteita ${uusiSijoittelu.hakukohteet.size}")
    uusiSijoittelu.hakukohteet
      .filter(_.kaikkiJonotSijoiteltu)
      .size mustEqual vanhaSijoittelu.hakukohteet.filter(_.kaikkiJonotSijoiteltu).size

    uusiSijoittelu.hakukohteet.foreach(uusiHakukohde => {
      vanhaSijoittelu.hakukohteet.find(_.oid.equals(uusiHakukohde.oid)).isDefined must be_==(true)
        .setMessage(s"Hakukohde ${uusiHakukohde.oid} puuttuu")
    })
    true mustEqual true
  }

  "Hakukohde in new sijoittelu should equal to hakukohde in old sijoittelu" in {
    logger.info(s"HAKUKOHDE ${hakukohdeOid}")
    val uusiHakukohde = time("Get uusi hakukohde") {
      get[Hakukohde](() => getNewHakukohde(hakuOid, hakukohdeOid, vtsSessionCookie))
    }
    val vanhaHakukohde = time("Get vanha hakukohde") {
      get[Hakukohde](() =>
        getOld(
          s"$oldSijoitteluHost/sijoittelu-service/resources/sijoittelu/$hakuOid/sijoitteluajo/latest/hakukohde/$hakukohdeOid"
        )
      )
    }

    uusiHakukohde.sijoitteluajoId mustEqual vanhaHakukohde.sijoitteluajoId
    uusiHakukohde.tila mustEqual vanhaHakukohde.tila
    uusiHakukohde.tarjoajaOid mustEqual None // tarjoaja oids are only fetched from tarjonta on demand these days
    uusiHakukohde.kaikkiJonotSijoiteltu mustEqual vanhaHakukohde.kaikkiJonotSijoiteltu
    uusiHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual vanhaHakukohde.ensikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet

    uusiHakukohde.valintatapajonot.size mustEqual vanhaHakukohde.valintatapajonot.size
    uusiHakukohde.valintatapajonot.foreach(uusiValintatapajono => {
      logger.info(s"Valintatapajono ${uusiValintatapajono.oid}")
      val vanhaValintatapajono =
        vanhaHakukohde.valintatapajonot.find(_.oid.equals(uusiValintatapajono.oid)).get
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
      uusiValintatapajono.hakemukset.foreach(uusiHakemus => {
        logger.info(s"Hakemus ${uusiHakemus.hakemusOid}")
        val vanhaHakemus =
          vanhaValintatapajono.hakemukset.find(_.hakemusOid.equals(uusiHakemus.hakemusOid)).get
        uusiHakemus must matchA[Hakemus]
          .hakijaOid(vanhaHakemus.hakijaOid)
          .pisteet(vanhaHakemus.pisteet)
          .paasyJaSoveltuvuusKokeenTulos(vanhaHakemus.paasyJaSoveltuvuusKokeenTulos)
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

        logger.info(s"Tilankuvaukset ${uusiHakemus.tilanKuvaukset}")

        uusiHakemus.tilanKuvaukset must matchA[Tilankuvaus]
          .EN(vanhaHakemus.tilanKuvaukset.EN)
          .FI(vanhaHakemus.tilanKuvaukset.FI)
          .SV(vanhaHakemus.tilanKuvaukset.SV)

        uusiHakemus.pistetiedot.size mustEqual vanhaHakemus.pistetiedot.size
        uusiHakemus.pistetiedot.foreach(uusiPistetieto => {
          val vanhaPistetieto =
            vanhaHakemus.pistetiedot.find(_.tunniste.equals(uusiPistetieto.tunniste)).get
          logger.info(s"Pistetieto ${uusiPistetieto.tunniste}")
          uusiPistetieto must matchA[Pistetieto]
            .tunniste(vanhaPistetieto.tunniste)
            .arvo(vanhaPistetieto.arvo)
            .laskennallinenArvo(vanhaPistetieto.laskennallinenArvo)
            .osallistuminen(vanhaPistetieto.osallistuminen)
            .tyypinKoodiUri(vanhaPistetieto.tyypinKoodiUri)
            .tilastoidaan(vanhaPistetieto.tilastoidaan)
        })

        logger.info(s"Tilahistoria ${uusiHakemus.tilaHistoria}")
        if (uusiHakemus.tilaHistoria.size > vanhaHakemus.tilaHistoria.size) {
          logger.info(
            s"vanhaHakemus.tilaHistoria: ${vanhaHakemus.hakemusOid} / ${vanhaHakemus.valintatapajonoOid} : ${vanhaHakemus.tilaHistoria}"
          )
          logger.info(
            s"uusiHakemus.tilaHistoria: ${uusiHakemus.hakemusOid} / ${uusiHakemus.valintatapajonoOid} : ${uusiHakemus.tilaHistoria}"
          )
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
      logger.info("uusiHakukohde.hakijaryhmat:")
      uusiHakukohde.hakijaryhmat.foreach { h => logger.info(s"\t$h") }
      logger.info("vanhaHakukohde.hakijaryhmat:")
      vanhaHakukohde.hakijaryhmat.foreach { h => logger.info(s"\t$h") }
    }
    uusiHakukohde.hakijaryhmat.size mustEqual vanhaHakukohde.hakijaryhmat.size
    uusiHakukohde.hakijaryhmat.foreach(uusiHakijaryhma => {
      logger.info(s"Hakijaryhma ${uusiHakijaryhma.oid}")
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
    true mustEqual true
  }

  override def getOld(uriString: String) = {
    val result = super.getOld(uriString)
    println(result)
    result
  }

  private def getNewSijoitteluajo(hakuOid: String, vtsSessionCookie: String) = {
    val url =
      vtsHost + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest/perustiedot"
    val result = time("Uuden sijoitteluajon perustietojen haku") { getNew(url, vtsSessionCookie) }
    println(result)
    result
  }

  private def getNewHakukohde(hakuOid: String, hakukohdeOid: String, vtsSessionCookie: String) = {
    val url =
      vtsHost + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest/hakukohde/$hakukohdeOid"
    val result = time("Uuden hakukohteen haku") { getNew(url, vtsSessionCookie) }
    println(result)
    result
  }

  private def getNewHakemus(hakuOid: String, hakemusOid: String, vtsSessionCookie: String) = {
    val url =
      vtsHost + s"/valinta-tulos-service/auth/sijoittelu/$hakuOid/sijoitteluajo/latest/hakemus/$hakemusOid"
    val result = time("Uuden hakemuksen haku") { getNew(url, vtsSessionCookie) }
    println(result)
    result
  }
}
