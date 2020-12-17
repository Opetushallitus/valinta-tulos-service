package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ServletSpecification
import fi.vm.sade.valintatulosservice.ensikertalaisuus.EnsikertalaisuusServlet
import fi.vm.sade.valintatulosservice.valintarekisteri.ValintarekisteriDbTools
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  EiEnsikertalainen,
  EiKktutkintoonJohtavaHakukohde,
  Ensikertalainen,
  Ensikertalaisuus,
  HakuOid,
  HakukohdeOid,
  Kevat,
  YPSHakukohde
}
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.jackson.Serialization._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import org.specs2.specification.After
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class EnsikertalaisuusServletSpec
    extends ServletSpecification
    with ValintarekisteriDbTools
    with After {
  override implicit val formats = EnsikertalaisuusServlet.ensikertalaisuusJsonFormats
  val henkilo = "1.2.246.562.24.00000000001"
  val vastaanottamaton_henkilo = "1.2.246.562.24.00000000002"
  val vanha_henkilo = "1.2.246.562.24.00000000003"
  val hakukohdekk = "1.2.246.561.20.00000000001"
  val hakukohde2aste = "1.2.246.561.20.00000000002"
  val vanha_hakukohde = "Vanhan hakukohteen nimi:101"
  val vanha_tarjoaja = "1.2.246.562.10.00000000001"
  val haku = "1.2.246.561.29.00000000001"
  val koulutus = "1.2.246.561.21.00000000001"
  val timestamp = new DateTime(2014, 7, 1, 16, 0, 10, DateTimeZone.forID("Europe/Helsinki"))
  val vanha_timestamp = new DateTime(2014, 6, 19, 16, 0, 10, DateTimeZone.forID("Europe/Helsinki"))

  step(deleteAll())
  step({
    singleConnectionValintarekisteriDb.storeHakukohde(
      YPSHakukohde(HakukohdeOid(hakukohdekk), HakuOid(haku), Kevat(2015))
    )
    singleConnectionValintarekisteriDb.storeHakukohde(
      EiKktutkintoonJohtavaHakukohde(HakukohdeOid(hakukohde2aste), HakuOid(haku), Some(Kevat(2015)))
    )
    singleConnectionValintarekisteriDb.runBlocking(
      DBIOAction
        .seq(
          sqlu"""insert into vastaanotot
                 (henkilo, hakukohde, action, ilmoittaja, "timestamp", selite)
                 values ($henkilo, $hakukohdekk, 'VastaanotaSitovasti'::vastaanotto_action, 'ilmoittaja', ${new java.sql.Timestamp(
            timestamp.getMillis
          )}, 'testiselite')""",
          sqlu"""insert into vastaanotot
                     (henkilo, hakukohde, action, ilmoittaja, "timestamp", selite)
                     values ($henkilo, $hakukohde2aste, 'VastaanotaSitovasti'::vastaanotto_action, 'ilmoittaja', ${new java.sql.Timestamp(
            timestamp.minusYears(1).getMillis
          )}, 'testiselite')""",
          sqlu"""insert into vanhat_vastaanotot (henkilo, hakukohde, tarjoaja, koulutuksen_alkamiskausi, kk_tutkintoon_johtava, ilmoittaja, "timestamp")
                 values ($henkilo, $vanha_hakukohde, $vanha_tarjoaja, '2014S', true, 'KAYTTAJA', ${new java.sql.Timestamp(
            vanha_timestamp.getMillis
          )})""",
          sqlu"""insert into vanhat_vastaanotot (henkilo, hakukohde, tarjoaja, koulutuksen_alkamiskausi, kk_tutkintoon_johtava, ilmoittaja, "timestamp")
                 values ($vanha_henkilo, $vanha_hakukohde, $vanha_tarjoaja, '2014S', true, 'KAYTTAJA', ${new java.sql.Timestamp(
            vanha_timestamp.getMillis
          )})"""
        )
        .transactionally
    )
  })

  "GET /ensikertalaisuus/:henkiloOid" should {
    "return 200 OK" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001",
        Map("koulutuksenAlkamiskausi" -> "2015K"),
        Map("Content-Type" -> "application/json")
      ) {
        status mustEqual 200
      }
    }

    "return EiEnsikertalainen" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001",
        Map("koulutuksenAlkamiskausi" -> "2015K"),
        Map("Content-Type" -> "application/json")
      ) {
        body mustEqual """{"personOid":"1.2.246.562.24.00000000001","paattyi":"2014-07-01T13:00:10Z"}"""
        read[EiEnsikertalainen](body) mustEqual EiEnsikertalainen(henkilo, timestamp.toDate)
      }
    }

    "return Ensikertalainen" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000002",
        Map("koulutuksenAlkamiskausi" -> "2015K"),
        Map("Content-Type" -> "application/json")
      ) {
        read[Ensikertalaisuus](body) mustEqual Ensikertalainen(vastaanottamaton_henkilo)
      }
    }

    "return EiEnsikertalainen based on vanhat_vastaanotot" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001",
        Map("koulutuksenAlkamiskausi" -> "2014K"),
        Map("Content-Type" -> "application/json")
      ) {
        read[Ensikertalaisuus](body) mustEqual EiEnsikertalainen(henkilo, vanha_timestamp.toDate)
      }
    }

    "return 400 Bad Request for invalid henkilo oid" in {
      get(
        "ensikertalaisuus/foo",
        Map("koulutuksenAlkamiskausi" -> "2015K"),
        Map("Content-Type" -> "application/json")
      ) {
        status mustEqual 400
      }
    }

    "return 400 Bad Request for invalid koulutuksenAlkamiskausi" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001",
        Map("koulutuksenAlkamiskausi" -> "foo"),
        Map("Content-Type" -> "application/json")
      ) {
        status mustEqual 400
      }
    }

    "return 400 Bad Request for missing koulutuksenAlkamiskausi" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001",
        Map(),
        Map("Content-Type" -> "application/json")
      ) {
        status mustEqual 400
      }
    }
  }

  "GET /ensikertalaisuus/:henkiloOid/historia" should {
    "return 200 OK" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001/historia",
        Map(),
        Map("Content-Type" -> "application/json")
      ) {
        status mustEqual 200
      }
    }

    "returns history of vastaanotot" in {
      get(
        "ensikertalaisuus/1.2.246.562.24.00000000001/historia",
        Map(),
        Map("Content-Type" -> "application/json")
      ) {
        body mustEqual (
          """{"opintopolku":[{"personOid":"1.2.246.562.24.00000000001","hakuOid":"1.2.246.561.29.00000000001","hakukohdeOid":"1.2.246.561.20.00000000001","vastaanottoAction":"VastaanotaSitovasti","vastaanottoaika":"2014-07-01T13:00:10Z"}],"""
            + """"vanhat":[{"personOid":"1.2.246.562.24.00000000001","hakukohde":"Vanhan hakukohteen nimi:101","vastaanottoaika":"2014-06-19T13:00:10Z"}]}"""
        )
      }
    }

    "return 400 Bad Request for invalid henkilo oid" in {
      get("ensikertalaisuus/foo/historia", Map(), Map("Content-Type" -> "application/json")) {
        status mustEqual 400
      }
    }
  }

  "POST /ensikertalaisuus" should {
    "return 200 OK" in {
      postJSON("ensikertalaisuus?koulutuksenAlkamiskausi=2014K", write(Seq(henkilo)), Map()) {
        status mustEqual 200
      }
    }

    "return a sequence of EiEnsikertalainen" in {
      val personOidsToQuery = Seq(henkilo, vastaanottamaton_henkilo, vanha_henkilo)
      postJSON("ensikertalaisuus?koulutuksenAlkamiskausi=2015K", write(personOidsToQuery), Map()) {
        val ensikertalaisuuses = read[Seq[Ensikertalaisuus]](body).sortBy(_.personOid)
        ensikertalaisuuses must have size 3
        ensikertalaisuuses.head mustEqual EiEnsikertalainen(henkilo, timestamp.toDate)
        ensikertalaisuuses(1) mustEqual Ensikertalainen(vastaanottamaton_henkilo)
        ensikertalaisuuses(2) mustEqual Ensikertalainen(vanha_henkilo)
      }
    }

    "return a sequence of EiEnsikertalainen based on vanhat_vastaanotot" in {
      val personOidsToQuery = Seq(henkilo, vastaanottamaton_henkilo, vanha_henkilo)
      postJSON("ensikertalaisuus?koulutuksenAlkamiskausi=2014K", write(personOidsToQuery), Map()) {
        val ensikertalaisuuses = read[Seq[Ensikertalaisuus]](body).sortBy(_.personOid)
        ensikertalaisuuses must have size 3
        ensikertalaisuuses.head mustEqual EiEnsikertalainen(henkilo, vanha_timestamp.toDate)
        ensikertalaisuuses(1) mustEqual Ensikertalainen(vastaanottamaton_henkilo)
        ensikertalaisuuses(2) mustEqual EiEnsikertalainen(vanha_henkilo, vanha_timestamp.toDate)
      }
    }

    "return 400 Bad Request if too many henkilo oids is sent" in {
      val tooManyPersonOids = appConfig.settings.valintaRekisteriEnsikertalaisuusMaxPersonOids + 1
      postJSON(
        "ensikertalaisuus?koulutuksenAlkamiskausi=2015K",
        write((1 to tooManyPersonOids).map(i => s"1.2.246.562.24.$i")),
        Map()
      ) {
        status mustEqual 400
      }
    }
  }

  override def after: Unit = deleteAll()
}
