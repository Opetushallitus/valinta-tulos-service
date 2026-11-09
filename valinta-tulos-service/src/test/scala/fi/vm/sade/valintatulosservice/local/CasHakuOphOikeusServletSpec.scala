package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.security.Role
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import org.json4s.Formats
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * GET /cas/haku/:hakuOid/hyvaksytyt, GET /cas/haku/:hakuOid/hakemus/:hakemusOid ja
 * POST /cas/haku/:hakuOid/hakemus/:hakemusOid/ilmoittaudu vaativat CRUD-oikeuden juuriorganisaatiossa. Pelkkä CRUD-rooli
 * tai CRUD-oikeus muussa organisaatiossa ei riitä. Tarkistus tehdään CAS-suodattimessa (CasFilter), ei servletissä.
 */
@RunWith(classOf[JUnitRunner])
class CasHakuOphOikeusServletSpec extends ServletSpecification with ValintarekisteriDbTools {

  override implicit val formats: Formats = JsonFormats.jsonFormats

  val hakuOid = "1.2.246.562.5.2013080813081926341928"
  val hakemusOid = "1.2.246.562.11.00000441369"
  val hakukohdeOid = "1.2.246.562.5.72607738902"
  val hakijaOid = "1.2.246.562.24.14229104472"

  lazy val ophSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD, Role.VALINTATULOSSERVICE_CRUD_OPH))
  lazy val organisaatioSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD, Role(s"${Role.VALINTATULOSSERVICE_CRUD.s}_1.2.246.562.10.111")))
  lazy val pelkkaCrudSessio = createTestSession(Set(Role.VALINTATULOSSERVICE_CRUD))

  step(useFixture("hyvaksytty-kesken-julkaistavissa.json"))

  "GET /cas/haku/:hakuOid/hyvaksytyt" should {
    "palauttaa hyväksytyt juuriorganisaation oikeudella" in {
      get(s"cas/haku/$hakuOid/hyvaksytyt", Seq.empty, otsakkeet(ophSessio)) {
        status must_== 200
        (JsonMethods.parse(body) \ "totalCount").extract[Int] must be_>(0)
      }
    }
    "vastaa 403 muun organisaation oikeudella" in {
      get(s"cas/haku/$hakuOid/hyvaksytyt", Seq.empty, otsakkeet(organisaatioSessio)) { status must_== 403 }
    }
    "vastaa 403 pelkällä CRUD-roolilla" in {
      get(s"cas/haku/$hakuOid/hyvaksytyt", Seq.empty, otsakkeet(pelkkaCrudSessio)) { status must_== 403 }
    }
  }

  "GET /cas/haku/:hakuOid/hakemus/:hakemusOid" should {
    "palauttaa hakemuksen tuloksen juuriorganisaation oikeudella" in {
      get(s"cas/haku/$hakuOid/hakemus/$hakemusOid", Seq.empty, otsakkeet(ophSessio)) {
        status must_== 200
        (JsonMethods.parse(body) \ "hakemusOid").extract[String] must_== hakemusOid
      }
    }
    "vastaa 403 muun organisaation oikeudella" in {
      get(s"cas/haku/$hakuOid/hakemus/$hakemusOid", Seq.empty, otsakkeet(organisaatioSessio)) { status must_== 403 }
    }
    "vastaa 403 pelkällä CRUD-roolilla" in {
      get(s"cas/haku/$hakuOid/hakemus/$hakemusOid", Seq.empty, otsakkeet(pelkkaCrudSessio)) { status must_== 403 }
    }
  }

  "POST /cas/haku/:hakuOid/hakemus/:hakemusOid/ilmoittaudu" should {
    "vastaa 403 muun organisaation oikeudella" in {
      ilmoittaudu(organisaatioSessio) { status must_== 403 }
    }
    "vastaa 403 pelkällä CRUD-roolilla" in {
      ilmoittaudu(pelkkaCrudSessio) { status must_== 403 }
    }
    "sallii ilmoittautumisen juuriorganisaation oikeudella" in {
      postJSON(s"vastaanotto/henkilo/$hakijaOid/hakemus/$hakemusOid/hakukohde/$hakukohdeOid", """{"action":"VastaanotaSitovasti"}""") {
        status must_== 200
      }
      ilmoittaudu(ophSessio) { status must_== 200 }
    }
  }

  step(deleteAll)

  private def otsakkeet(sessio: String): Map[String, String] = Map("Cookie" -> s"session=$sessio")

  private def ilmoittaudu[T](sessio: String)(tarkista: => T): T =
    postJSON(s"cas/haku/$hakuOid/hakemus/$hakemusOid/ilmoittaudu",
      s"""{"hakukohdeOid":"$hakukohdeOid","tila":"LASNA_KOKO_LUKUVUOSI","muokkaaja":"testi","selite":"Käyttöoikeustesti"}""",
      otsakkeet(sessio))(tarkista)
}
