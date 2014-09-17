package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.config.AppConfig.AppConfig
import fi.vm.sade.valintatulosservice.domain.{Vastaanottotila, Hakemuksentulos}
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.specification.{Step, Fragments}
import org.json4s.jackson.Serialization

class ValintaTulosServletSpec extends MutableScalatraSpec {
  implicit val appConfig: AppConfig = new AppConfig.IT
  implicit val formats = JsonFormats.jsonFormats

  "GET /haku/:hakuId/hakemus/:hakemusId" should {
    "palauttaa valintatulokset" in {
      get("/haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
        body must_== """{"hakemusOid":"1.2.246.562.11.00000441369","hakutoiveet":[{"hakukohdeOid":"1.2.246.562.5.72607738902","tarjoajaOid":"1.2.246.562.10.591352080610","valintatila":"HYVAKSYTTY","vastaanottotila":"KESKEN","ilmoittautumistila":"EI_TEHTY","vastaanotettavuustila":"VASTAANOTETTAVISSA_SITOVASTI","jonosija":1,"varasijojaKaytetaanAlkaen":"2014-08-26T19:05:23Z","varasijojaTaytetaanAsti":"2014-08-26T19:05:23Z"},{"hakukohdeOid":"1.2.246.562.5.16303028779","tarjoajaOid":"1.2.246.562.10.455978782510","valintatila":"KESKEN","vastaanottotila":"KESKEN","ilmoittautumistila":"EI_TEHTY","vastaanotettavuustila":"EI_VASTAANOTETTAVISSA"}]}"""
      }
    }
  }

  "POST /haku:hakuId/hakemus/:hakemusId/vastaanota" should {
    "vastaanottaa opiskelupaikan" in {
      post("/haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369/vastaanota",
        """{"hakukohdeOid":"1.2.246.562.5.72607738902","tila":"VASTAANOTTANUT","muokkaaja":"Teppo Testi","selite":"Testimuokkaus"}""".getBytes("UTF-8"), Map("Content-type" -> "application/json")) {
        status must_== 200

        get("/haku/1.2.246.562.5.2013080813081926341928/hakemus/1.2.246.562.11.00000441369") {
          val tulos: Hakemuksentulos = Serialization.read[Hakemuksentulos](body)
          tulos.hakutoiveet.head.vastaanottotila must_== Vastaanottotila.vastaanottanut
        }
      }
    }
  }

  addServlet(new ValintatulosServlet(), "/*")
  override def map(fs: => Fragments) = {
    Step(appConfig.start) ^ super.map(fs)
  }
}