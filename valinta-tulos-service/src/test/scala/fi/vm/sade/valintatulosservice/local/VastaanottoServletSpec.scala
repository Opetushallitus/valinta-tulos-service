package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.domain.{Hakemuksentulos, Valintatila}
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOidSerializer, HakuOidSerializer, HakukohdeOidSerializer, ValintatapajonoOidSerializer, Vastaanottotila}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class VastaanottoServletSpec extends ServletSpecification with ValintarekisteriDbTools {

  override implicit val formats: Formats = JsonFormats.jsonFormats

  "POST /vastaanotto" should {
    "vastaanottaa opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("VastaanotaSitovasti") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }

    "peruu opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanota("Peru") {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== "PERUNUT"
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System.currentTimeMillis() +/- 4000)
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
          tulos.hakutoiveet.head.vastaanottotila must_== "KESKEN"
          tulos.hakutoiveet.last.vastaanottotila must_== "EHDOLLISESTI_VASTAANOTTANUT"
          val muutosAika = tulos.hakutoiveet.last.viimeisinValintatuloksenMuutos.get
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos must beNone
          muutosAika.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }
  }

  "POST /auth/vastaanotto" should {

    lazy val testSession: String = createTestSession(roles = Set(Role.VALINTATULOSSERVICE_CRUD))
    lazy val authHeaders = Map("Cookie" -> s"session=${testSession}")

    "vaatii autentikoinnin" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanotaAuthenticated("VastaanotaSitovasti") {
        status must_== 401
      }
    }

    "vaatii autorisoinnin" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      lazy val testSessionWithInadequateCredentials: String = createTestSession(roles = Set(Role.SIJOITTELU_CRUD))
      lazy val headers = Map("Cookie" -> s"session=${testSessionWithInadequateCredentials}")

      vastaanotaAuthenticated("VastaanotaSitovasti", headers = headers) {
        status must_== 403
      }
    }

    "vastaanottaa opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanotaAuthenticated("VastaanotaSitovasti", headers = authHeaders) {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }

    "peruu opiskelupaikan" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json")

      vastaanotaAuthenticated("Peru", headers = authHeaders) {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== "PERUNUT"
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.isDefined must beTrue
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos.get.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }

    "vastaanottaa ehdollisesti" in {
      useFixture("hyvaksytty-ylempi-varalla.json")

      vastaanotaAuthenticated("VastaanotaEhdollisesti", hakukohde = "1.2.246.562.5.16303028779", headers = authHeaders) {
        status must_== 200

        get("haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.valintatila must_== Valintatila.varalla
          tulos.hakutoiveet.head.vastaanottotila must_== "KESKEN"
          tulos.hakutoiveet.last.vastaanottotila must_== "EHDOLLISESTI_VASTAANOTTANUT"
          val muutosAika = tulos.hakutoiveet.last.viimeisinValintatuloksenMuutos.get
          tulos.hakutoiveet.head.viimeisinValintatuloksenMuutos must beNone
          muutosAika.getTime must be ~ (System.currentTimeMillis() +/- 4000)
        }
      }
    }
  }

  def vastaanota[T](action: String, hakukohde: String = "1.2.246.562.5.72607738902", personOid: String = "1.2.246.562.24.14229104472", hakemusOid: String = "1.2.246.562.11.00000441369")(block: => T): T = {
    postJSON(s"""vastaanotto/henkilo/$personOid/hakemus/$hakemusOid/hakukohde/$hakukohde""",
      s"""{"action":"$action"}""") {
      block
    }
  }

  def vastaanotaAuthenticated[T](action: String, hakukohde: String = "1.2.246.562.5.72607738902", hakemusOid: String = "1.2.246.562.11.00000441369", headers: Map[String, String] = Map.empty)(block: => T): T = {
    postJSON(s"""auth/vastaanotto/hakemus/$hakemusOid/hakukohde/$hakukohde""",
      s"""{"action":"$action"}""", headers) {
      block
    }
  }
}
