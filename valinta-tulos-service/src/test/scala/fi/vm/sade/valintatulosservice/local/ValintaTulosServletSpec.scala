package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.security.mock.MockSecurityContext
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.hakemus.AtaruHakemus
import fi.vm.sade.valintatulosservice.oppijanumerorekisteri.Henkilo
import fi.vm.sade.valintatulosservice.production.Hakija
import fi.vm.sade.valintatulosservice.tarjonta.HakuFixtures
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.JValue
import org.json4s.JsonAST.JArray
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods
import org.json4s.native.JsonMethods.{compact, render}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintaTulosServletSpec extends ServletSpecification {
  val ataruHakemus1 = AtaruHakemus(HakemusOid("1.2.246.562.11.00000000000000000005"),
    HakuOid("1.2.246.562.29.37061034627"), List(HakukohdeOid("1.2.246.562.20.14875157126")), HakijaOid("ataru-tyyppi"), "fi", "test@example.com")
  val ataruHakemus2 = AtaruHakemus(HakemusOid("1.2.246.562.11.00000000000000000006"),
    HakuOid("1.2.246.562.29.37061034627"), List(HakukohdeOid("1.2.246.562.20.14875157126"), HakukohdeOid("1.2.246.562.20.27958725015")), HakijaOid("ataru-tyyppi2"), "fi", "test@example.com")
  val ataruHenkilo1 = Henkilo(HakijaOid("ataru-tyyppi"), None, Some("Ataru"))
  val ataruHenkilo2 = Henkilo(HakijaOid("ataru-tyyppi2"), None, Some("Ataru2"))

  "GET /haku/:hakuOid/hakukohde/:hakukohdeOid" should {
    "palauttaa julkaistun yksittäisen hakukohteen valintatulokset" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928/hakukohde/1.2.246.562.5.72607738902") {
        status must_== 200
        body must_== """[{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441370","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441371","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}]"""
      }
    }

    "palauttaa ehdollisesti hyväksytyn hakukohteen valintatulokset" in {
      useFixture("hyvaksytty-ehdollisesti-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928/hakukohde/1.2.246.562.5.72607738902") {
        status must_== 200
        body must_== """[{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":true,"ehdollisenHyvaksymisenEhtoKoodi":"muu","ehdollisenHyvaksymisenEhtoFI":"muu","ehdollisenHyvaksymisenEhtoSV":"andra","ehdollisenHyvaksymisenEhtoEN":"other","tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441370","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441371","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}]"""
      }
    }

    "palauttaa Ataru-hakemusten tiedot" in {
      val ataruHakemukset = List(ataruHakemus1, ataruHakemus2)
      val ataruHenkilot = List(ataruHenkilo1, ataruHenkilo2)
      useFixture("ei-tuloksia.json", hakemusFixtures = List.empty, hakuFixture = HakuOid("ataru-haku"),
        ataruHakemusFixture = ataruHakemukset, ataruHenkiloFixture = ataruHenkilot)
      get("haku/1.2.246.562.29.37061034627/hakukohde/1.2.246.562.20.14875157126") {
        status must_== 200
        body must_== """[{"hakuOid":"1.2.246.562.29.37061034627","hakemusOid":"1.2.246.562.11.00000000000000000005","hakijaOid":"ataru-tyyppi","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.20.14875157126","hakukohdeNimi":"Ataru testihakukohde","tarjoajaOid":"1.2.246.562.10.72985435253","tarjoajaNimi":"Aalto-yliopisto, Insinööritieteiden korkeakoulu","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oiliHetuton/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.29.37061034627","hakemusOid":"1.2.246.562.11.00000000000000000006","hakijaOid":"ataru-tyyppi2","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.20.14875157126","hakukohdeNimi":"Ataru testihakukohde","tarjoajaOid":"1.2.246.562.10.72985435253","tarjoajaNimi":"Aalto-yliopisto, Insinööritieteiden korkeakoulu","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oiliHetuton/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.27958725015","hakukohdeNimi":"Ataru testihakukohde","tarjoajaOid":"1.2.246.562.10.72985435253","tarjoajaNimi":"Aalto-yliopisto, Insinööritieteiden korkeakoulu","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oiliHetuton/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}]"""
      }
    }

    "kun hakukohdetta ei löydy" in {
      "404" in {
        HakuFixtures.useFixture(HakuOid("notfound"))
        get("haku/1.2.246.562.5.2013080813081926341928/hakukohde/1.2.246.562.5.foo") {
          status must_== 404
          body must_== """{"error":"Not found"}"""
        }
      }
    }
  }

  "GET /haku/:hakuId/hakemus/:hakemusId" should {
    "palauttaa julkaistun valintatuloksen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        status must_== 200
        body must_==
          """{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}"""
      }
    }

    "palauttaa ei-julkaistun hyvaksytyn valintatuloksen, joka ei ole julkaistavissa, KESKEN-tilaisena, vaikka haun vastaanottopvm olisi mennyt" in {
      useFixture("hyvaksytty-kesken-ei-julkaistavissa.json", ohjausparametritFixture = "vastaanotto-loppunut")

      get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        status must_== 200
        body must_==
          """{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2014-09-01T09:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","viimeisinHakemuksenTilanMuutos":"2013-08-26T15:12:40Z","varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}"""
      }

    }

    "palauttaa ehdollisesti hyväksytyn valintatuloksen" in {
      useFixture("hyvaksytty-ehdollisesti-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        status must_== 200
        body must_==
          """{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":true,"ehdollisenHyvaksymisenEhtoKoodi":"muu","ehdollisenHyvaksymisenEhtoFI":"muu","ehdollisenHyvaksymisenEhtoSV":"andra","ehdollisenHyvaksymisenEhtoEN":"other","tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}"""
      }
    }

    "palauttaa ehdollisesti hyväksytyn syyn valintatuloksen" in {
      useFixture("hyvaksytty-ehdollisesti-syy-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        status must_== 200
        body must_==
          """{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":true,"ehdollisenHyvaksymisenEhtoKoodi":"hyvaksynnanehdot_muu","ehdollisenHyvaksymisenEhtoFI":"ehto suomi","ehdollisenHyvaksymisenEhtoSV":"ehto ruotsi","ehdollisenHyvaksymisenEhtoEN":"ehto englanti","tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}"""
      }
    }

    "kun hakemusta ei löydy" in {
      "404" in {
        useFixture("hyvaksytty-ehdollisesti-syy-kesken-julkaistavissa.json")
        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.LOLLERSTRÖM") {
          body must_== """{"error":"Not found"}"""
          status must_== 404
        }
      }
    }

    "palauttaa ataru-hakemuksen valintatuloksen" in {
      useFixture("ei-tuloksia.json", hakemusFixtures = List.empty, hakuFixture = HakuOid("ataru-haku"),
        ataruHakemusFixture = List(ataruHakemus1), ataruHenkiloFixture = List(ataruHenkilo1))
      get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000000000000000005") {
        status must_== 200
        body must_==
          """{"hakuOid":"1.2.246.562.29.37061034627","hakemusOid":"1.2.246.562.11.00000000000000000005","hakijaOid":"ataru-tyyppi","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.20.14875157126","hakukohdeNimi":"Ataru testihakukohde","tarjoajaOid":"1.2.246.562.10.72985435253","tarjoajaNimi":"Aalto-yliopisto, Insinööritieteiden korkeakoulu","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oiliHetuton/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}"""
      }
    }
  }

  "GET /cas/haku/:hakuId/hakemus/:hakemusId" should {
    "estää pääsyn ilman tikettiä" in {
      get("cas/haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        status must_== 401
      }
    }
    "mahdolistaa pääsyn validilla tiketillä" in {
      get("cas/haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369", ("ticket", getTicket)) {
        status must_== 200
      }
    }
  }

  "GET /haku/:hakuOid" should {
    "palauttaa koko haun valintatulokset" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      get("haku/1.2.246.562.5.2013080813081926341928") {
        status must_== 200
        body must_== """[{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441369","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"14090336922663576781797489829886","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","vastaanottoDeadline":"2030-01-10T10:00:00Z","viimeisinHakemuksenTilanMuutos":"2014-08-26T15:12:40Z","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T16:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T16:05:23Z","julkaistavissa":true,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{},"pisteet":4.0},{"hakukohdeOid":"1.2.246.562.5.16303028779","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.455978782510","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"PERUUNTUNUT","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441370","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]},{"hakuOid":"1.2.246.562.5.2013080813081926341928","hakemusOid":"1.2.246.562.11.00000441371","hakijaOid":"1.2.246.562.24.14229104472","aikataulu":{"vastaanottoEnd":"2030-01-10T10:00:00Z","vastaanottoBufferDays":14},"hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","hakukohdeNimi":"stevari amk hakukohde","tarjoajaOid":"1.2.246.562.10.591352080610","tarjoajaNimi":"Saimaan ammattikorkeakoulu, Skinnarilan kampus, Lappeenranta","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}},{"hakukohdeOid":"1.2.246.562.20.83060182827","hakukohdeNimi":"","tarjoajaOid":"1.2.246.562.10.83122281013","tarjoajaNimi":"","valintatapajonoOid":"","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":{"ilmoittautumisaika":{"loppu":"2030-01-10T21:59:59Z"},"ilmoittautumistapa":{"nimi":{"fi":"Oili","sv":"Oili","en":"Oili"},"url":"/oili/"},"ilmoittautumistila":"EI_TEHTY","ilmoittauduttavissa":false},"vastaanotettavuustila":"EI_VASTAANOTETTAVISSA","julkaistavissa":false,"ehdollisestiHyvaksyttavissa":false,"tilanKuvaukset":{}}]}]"""
      }
    }

    "kun hakua ei löydy" in {
      "404" in {
        HakuFixtures.useFixture(HakuOid("notfound"))
        get("haku/1.2.246.562.5.foo") {
          status must_== 404
          body must_== """{"error":"Not found"}"""
        }
      }
    }
  }

  "GET /haku/:hakuOid/sijoitteluAjo/:sijoitteluAjoId/hakemukset" should {
    "palauttaa haun sijoitteluajon hakemusten tulokset vastaanottotiloineen" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      get("haku/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset") {
        val bodyJson = JsonMethods.parse(body)
        val tulos: List[Hakija] = (bodyJson \ "results").extract[List[Hakija]]

        tulos.head.hakutoiveet.head.vastaanottotieto must_== Some(Vastaanottotila.kesken)
        status must_== 200
      }

      vastaanota("VastaanotaSitovasti") {
        get("haku/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset") {
          val bodyJson = JsonMethods.parse(body)
          (bodyJson \ "totalCount").extract[Int] must_== 1
          stringInJson(bodyJson, "hakijaOid") must_== "1.2.246.562.24.14229104472"
          stringInJson(bodyJson, "hakemusOid") must_== "1.2.246.562.11.00000441369"

          val tulos: List[Hakija] = (bodyJson \ "results").extract[List[Hakija]]
          tulos.head.hakutoiveet.head.vastaanottotieto must_== Some(Vastaanottotila.vastaanottanut)
          status must_== 200
        }
      }
    }

    "kun haku ei löydy" in {
      "200 tyhjien tulosten kanssa" in {
        HakuFixtures.useFixture(HakuOid("notfound"))
        get("haku/1.2.246.562.5.foo/sijoitteluajo/latest/hakemukset") {
          body must_== """{"totalCount":0,"results":[]}"""
          status must_== 200
        }
      }
    }
  }

  "GET /haku/streaming/:hakuOid/sijoitteluAjo/:sijoitteluAjoId/hakemukset" should {

    def checkData() = {
      get("haku/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset") {
        val bodyJson = JsonMethods.parse(body)
        (bodyJson \ "totalCount").extract[Int] must_== 1
        stringInJson(bodyJson, "vastaanottotieto") must_== "KESKEN"
        stringInJson(bodyJson, "hakukohdeOid") must_== "1.2.246.562.5.72607738902"
        val valintatapajonot = (bodyJson \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray]
        valintatapajonot.arr.size must_== 2
        List(stringInJson(valintatapajonot.arr(0), "valintatapajonoOid"), stringInJson(valintatapajonot.arr(1), "valintatapajonoOid")) diff
          List("14090336922663576781797489829886", "14090336922663576781797489829887") must_== List()
        status must_== 200
      }
    }

    "palauttaa haun sijoitteluajon hakemusten tulokset vastaanottotiloineen" in {

      useFixture("hyvaksytty-kesken-julkaistavissa-korjattu.json")

      checkData()

      vastaanota("VastaanotaSitovasti") {
        get("haku/streaming/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset") {
          val streamedJson = JsonMethods.parse(body)
          stringInJson(streamedJson, "hakijaOid") must_== "1.2.246.562.24.14229104472"
          stringInJson(streamedJson, "hakemusOid") must_== "1.2.246.562.11.00000441369"
          stringInJson(streamedJson, "vastaanottotieto") must_== "VASTAANOTTANUT_SITOVASTI"
          (streamedJson \\ "hakutoiveet").asInstanceOf[JArray].arr.size must_== 1
          val valintatapajonot = (streamedJson \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray]
          valintatapajonot.arr.size must_== 2
          List(stringInJson(valintatapajonot.arr(0), "valintatapajonoOid"), stringInJson(valintatapajonot.arr(1), "valintatapajonoOid")) diff
            List("14090336922663576781797489829886", "14090336922663576781797489829887") must_== List()
          status must_== 200
        }
      }
    }

    "palauttaa haun sijoitteluajon hakemusten tulokset vain merkitsevälle jonolle" in {

      useFixture("hyvaksytty-kesken-julkaistavissa-korjattu.json")

      checkData()

      vastaanota("VastaanotaSitovasti") {
        get("haku/streaming/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true") {
          val streamedJson = JsonMethods.parse(body)
          stringInJson(streamedJson, "hakijaOid") must_== "1.2.246.562.24.14229104472"
          stringInJson(streamedJson, "hakemusOid") must_== "1.2.246.562.11.00000441369"
          stringInJson(streamedJson, "vastaanottotieto") must_== "VASTAANOTTANUT_SITOVASTI"
          (streamedJson \\ "hakutoiveet").asInstanceOf[JArray].arr.size must_== 1
          (streamedJson \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray].arr.size must_== 1
          stringInJson(streamedJson, "valintatapajonoOid") must_== "14090336922663576781797489829886"
          status must_== 200
        }
      }
    }

    "palauttaa haun sijoitteluajon keskeneräisen tuloksen merkitsevälle jonolle" in {

      useFixture("hyvaksytty-kesken-julkaistavissa-korjattu.json")

      checkData()

      get("haku/streaming/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true") {
        val streamedJson = JsonMethods.parse(body)
        stringInJson(streamedJson, "hakijaOid") must_== "1.2.246.562.24.14229104472"
        stringInJson(streamedJson, "hakemusOid") must_== "1.2.246.562.11.00000441369"
        stringInJson(streamedJson, "vastaanottotieto") must_== "KESKEN"
        (streamedJson \\ "hakutoiveet").asInstanceOf[JArray].arr.size must_== 1
        (streamedJson \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray].arr.size must_== 1
        stringInJson(streamedJson, "valintatapajonoOid") must_== "14090336922663576781797489829886"
        status must_== 200
      }
    }

    "palauttaa haun hakukohteiden hakemusten tulokset vain merkitsevälle jonolle" in {

      useFixture("hyvaksytty-kesken-julkaistavissa-korjattu.json")

      checkData()

      vastaanota("VastaanotaSitovasti") {
        val hakukohdeOidsInPostBody = "[\"1.2.246.562.5.72607738902\"]".getBytes("UTF-8")
        post("haku/streaming/1.2.246.562.5.2013080813081926341928/sijoitteluajo/latest/hakemukset?vainMerkitsevaJono=true",
          hakukohdeOidsInPostBody,
          headers = Map("Content-type" -> "application/json")) {
            val streamedJson = JsonMethods.parse(body)
            stringInJson(streamedJson, "hakijaOid") must_== "1.2.246.562.24.14229104472"
            stringInJson(streamedJson, "hakemusOid") must_== "1.2.246.562.11.00000441369"
            stringInJson(streamedJson, "vastaanottotieto") must_== "VASTAANOTTANUT_SITOVASTI"
            (streamedJson \\ "hakutoiveet").asInstanceOf[JArray].arr.size must_== 1
            (streamedJson \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray].arr.size must_== 1
            stringInJson(streamedJson, "valintatapajonoOid") must_== "14090336922663576781797489829886"
            status must_== 200
        }
      }
    }
  }

  "POST /haku/:hakuId/hakemus/:hakemusId/ilmoittaudu" should {
    "merkitsee ilmoittautuneeksi" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("VastaanotaSitovasti") {
        ilmoittaudu("LASNA_KOKO_LUKUVUOSI") {
          status must_== 200

          get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
            val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
            tulos.hakutoiveet.head.ilmoittautumistila must_== HakutoiveenIlmoittautumistila(Ilmoittautumisaika(None, Some(new DateTime(2030, 1, 10, 21, 59, 59, DateTimeZone.UTC))), None, LasnaKokoLukuvuosi, false)
            tulos.hakutoiveet.head.ilmoittautumisenAikaleima.get.getTime() must be ~ (System.currentTimeMillis() +/- 2000)
          }
        }
      }
    }

    "hyväksyy ilmoittautumisen vain jos vastaanotettu ja ilmoittauduttavissa" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      ilmoittaudu("LASNA_KOKO_LUKUVUOSI") {
        body must_== """{"error":"Hakutoive 1.2.246.562.5.72607738902 ei ole ilmoittauduttavissa: ilmoittautumisaika: {\"loppu\":\"2030-01-10T21:59:59Z\"}, ilmoittautumistila: EI_TEHTY, valintatila: HYVAKSYTTY, vastaanottotila: KESKEN"}"""
        status must_== 400
      }
    }

    "raportoi virheellisen pyynnön" in {
      postJSON("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369/ilmoittaudu",
        ("oops")) {
        body must startWith("{\"error\":\"No usable value for hakukohdeOid")
        status must_== 400
      }
    }

    "raportoi puuttuvan/väärän content-typen" in {
      ilmoittaudu("LASNA_KOKO_LUKUVUOSI", headers = Map(("Content-type" -> "application/xml"))) {
        body must startWith("{\"error\":\"Only application/json accepted")
        status must_== 415
      }
    }
  }

  "POST /cas/haku/:hakuId/hakemus/:hakemusId/ilmoittaudu" should {
    "estää pääsyn ilman tikettiä" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      ilmoittaudu("LASNA_KOKO_LUKUVUOSI", juuri = "cas/haku") {
        status must_== 401
        body must_== """{"error":"Unauthorized"}"""
      }
    }

    "toimii tiketillä" in {
      vastaanota("VastaanotaSitovasti") {
        ilmoittaudu("LASNA_KOKO_LUKUVUOSI", juuri = "cas/haku", headers = Map("ticket" -> getTicket)) {
          status must_== 200
        }
      }
    }
  }

  "POST /haku/:hakuId/hakemus/:hakemusId/vastaanota" should {
    "vastaanottaa opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("VastaanotaSitovasti") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
        }
      }
    }

    "peruu opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("Peru") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila.toString must_== "PERUNUT"
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime() must be ~ (System.currentTimeMillis() +/- 2000)
        }
      }
    }

    "vastaanottaa ehdollisesti" in {
      useFixture("hyvaksytty-ylempi-varalla.json")

      vastaanota("VastaanotaEhdollisesti", hakukohde = "1.2.246.562.5.16303028779") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.valintatila must_== Valintatila.varalla
          tulos.hakutoiveet.head.vastaanottotila.toString must_== "KESKEN"
          tulos.hakutoiveet.last.vastaanottotila.toString must_== "EHDOLLISESTI_VASTAANOTTANUT"
          val muutosAika = tulos.hakutoiveet.last.viimeisinValintatuloksenMuutos.get
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos must beNone
          muutosAika.getTime() must be ~ (System.currentTimeMillis() +/- 2000)
        }
      }
    }
  }

  "GET /haku/:hakuOid/ilmanHyvaksyntaa" should {
    "palauttaa oikea hylkäyksen syy" in {
      useFixture("hylatty-peruste-viimeisesta-jonosta.json")
      val hakuOid = "1.2.246.562.5.2013080813081926341928"
      get(s"haku/$hakuOid/ilmanHyvaksyntaa") {
        status must_== 200
        val streamedJson = JsonMethods.parse(body)

        val result = (((streamedJson \\ "results").asInstanceOf[JArray](0) \\ "hakutoiveet").asInstanceOf[JArray](0) \\ "hakutoiveenValintatapajonot").asInstanceOf[JArray](0) \\ "tilanKuvaukset"

        compact(render(result)) must_== """{"FI":"Toinen jono","SV":"Toinen jono sv","EN":"Toinen jono en"}"""
      }
    }
  }

  "GET /haku/:hakuOid/hyvaksytyt" should {
    "palauttaa hyvaksytyt hakemukset" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      val hakuOid = "1.2.246.562.5.2013080813081926341928"
      get(s"haku/$hakuOid/hyvaksytyt") {
        status must_== 200
        val responseJson = JsonMethods.parse(body)

        (responseJson \ "results" \ "hakijaOid").extract[Seq[HakijaOid]] must have size greaterThan(0)
        (responseJson \ "results" \\ "tila" \ "tila").extract[Seq[String]] must have size greaterThan(1)
        (responseJson \ "results" \\ "tila" \ "tila").extract[Seq[String]] must contain("HYVAKSYTTY")
      }
    }
  }


  def vastaanota[T](action: String, hakukohde: String = "1.2.246.562.5.72607738902", personOid: String = "1.2.246.562.24.14229104472", hakemusOid: String = "1.2.246.562.11.00000441369")(block: => T) = {
    postJSON(s"""vastaanotto/henkilo/$personOid/hakemus/$hakemusOid/hakukohde/$hakukohde""",
      s"""{"action":"$action"}""") {
      block
    }
  }

  def ilmoittaudu[T](tila: String, juuri:String = "haku", headers: Map[String, String] = Map.empty)(block: => T) = {
    postJSON(juuri + "/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369/ilmoittaudu",
      """{"hakukohdeOid":"1.2.246.562.5.72607738902","tila":""""+tila+"""","muokkaaja":"OILI","selite":"Testimuokkaus"}""", headers) {
      block
    }
  }

  def getTicket = {
    val ticket = MockSecurityContext.ticketFor(appConfig.settings.securitySettings.casServiceIdentifier, "testuser")
    ticket
  }

  private def stringInJson(json: JValue, fieldName: String): String = try {
    (json \\ fieldName).extract[String]
  } catch {
    case e: Exception =>
      System.err.println(s"Could not parse $fieldName from $json")
      throw e
  }
}
