package fi.vm.sade.valintatulosservice.json

import java.util.Date

import fi.vm.sade.valintatulosservice.{ServletSpecification, json4sCustomFormats}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.SijoitteluWrapper
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods.parse
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.springframework.core.io.ClassPathResource


@RunWith(classOf[JUnitRunner])
class JsonFormatsSpec extends ServletSpecification with json4sCustomFormats {

  val expected = new DateTime("2014-08-01T16:00:00.000Z").toDate


  "SijoitteluWrapper.fromJson" should {
    "Parse date correctly" in {
      val fixtureName = "hyvaksytty-ylempi-varalla.json"
      val json = parse(scala.io.Source.fromInputStream(new ClassPathResource("fixtures/sijoittelu/" + fixtureName).getInputStream).mkString)
      val wrapper = SijoitteluWrapper.fromJson(json).get

      val hakukohde = wrapper.hakukohteet.find(_.getOid == "1.2.246.562.5.16303028779").get
      hakukohde.getValintatapajonot.get(0).getVarasijojaKaytetaanAlkaen must_== expected
    }
  }
}
