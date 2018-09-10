package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.vastaanottomeili.{LahetysKuittaus, PollResult}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.specs2.matcher.MatchResult
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeEach
import slick.jdbc.PostgresProfile.api._


@RunWith(classOf[JUnitRunner])
class EmailStatusServletSpec extends ServletSpecification with ValintarekisteriDbTools with BeforeEach {
  override implicit val formats = JsonFormats.jsonFormats
  "GET /vastaanottoposti" should {
    "Lista lähetettävistä sähköposteista" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = List("00000441369"))

        verifyEmails { emails => {
            val ilmoitukset = parse(emails).extract[PollResult].mailables
            ilmoitukset must_!= null
            ilmoitukset.isEmpty must_== false
          }
        }
    }
    "Lista sisältää tiedon ehdollisesta vastaanotosta" in {
      useFixture("hyvaksytty-ehdollisesti-kesken-julkaistavissa.json", hakemusFixtures = List("00000441369"))

        verifyEmails { emails => {
            val ilmoitukset = parse(emails).extract[PollResult].mailables
            ilmoitukset must_!= null
            ilmoitukset.isEmpty must_== false
          }
        }
    }
    "Tyhjä lista lähetettävistä sähköposteista, kun ei lähetettävää" in {
      useFixture("hylatty-ei-valintatulosta.json", hakemusFixtures = List("00000441369"))
      withFixedDateTime("10.10.2014 12:00") {
        verifyEmptyListOfEmails
      }
    }

    "Ei lähetetä, jos email-osoite puuttuu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = List("00000441369-no-email"))
      verifyEmptyListOfEmails

      before()

      // tarkistetaan, että lähetetään myöhemmin jos email on lisätty
      hakemusFixtureImporter.clear.importFixture("00000441369")
      verifyNonEmptyListOfEmails
    }

    "Ei lähetetä, jos henkilötunnus puuttuu" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = List("00000441369-no-hetu"))

      verifyEmptyListOfEmails
    }
  }

  "POST /vastaanottoposti" should {
    "Merkitsee postitukset tehdyiksi" in {
      useFixture("hyvaksytty-kesken-julkaistavissa.json", hakemusFixtures = List("00000441369"))
      get("vastaanottoposti") {
        val mailsToSend = Serialization.read[PollResult](body).mailables
        mailsToSend.isEmpty must_== false
        withFixedDateTime("12.10.2014 12:00") {
          val kuittaukset = mailsToSend.map { mail =>
            LahetysKuittaus(mail.hakemusOid, mail.hakukohteet.map(_.oid), List("email"))
          }
          postJSON("vastaanottoposti", Serialization.write(kuittaukset)) {
            status must_== 200
            verifyEmptyListOfEmails
          }
        }
      }
    }
  }

  def verifyEmptyListOfEmails = {
    verifyEmails { emails => parse(emails).extract[PollResult].mailables must beEmpty }
  }

  def verifyNonEmptyListOfEmails = {
    verifyEmails { emails => parse(emails).extract[PollResult].mailables must not beEmpty }
  }


  def verifyEmails(check: String => MatchResult[Any]): MatchResult[Any] = {
    get("vastaanottoposti") {
      status must_== 200
      check(body)
    }
  }

  def expectEmails(expected: String): MatchResult[Any] = {
    verifyEmails { emails => emails must_== expected }
  }

  override def before(): Unit = {
    valintarekisteriDbWithPool.runBlockingTransactionally(sqlu"delete from viestinlahetys_tarkistettu")
  }
}
