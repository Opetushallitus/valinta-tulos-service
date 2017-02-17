package fi.vm.sade.valintatulosservice.local

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.TypeImports._
import fi.vm.sade.sijoittelu.domain.HakemuksenTila
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.domain._
import fi.vm.sade.valintatulosservice.generatedfixtures._
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.mongo.MongoFactory
import fi.vm.sade.valintatulosservice.sijoittelu.{DirectMongoSijoittelunTulosRestClient, SijoittelutulosService}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuFixtures, HakuService}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Vastaanottotila._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{VastaanottoEventDto, Vastaanottotila}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.vastaanottomeili._
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MailPollerSpec extends ITSpecification with TimeWarp {
  lazy val hakuService = HakuService(appConfig)
  lazy val valintarekisteriDb = new ValintarekisteriDb(appConfig.settings.valintaRekisteriDbConfig)
  lazy val sijoittelunTulosRestClient = new DirectMongoSijoittelunTulosRestClient(appConfig)
  lazy val sijoittelutulosService = new SijoittelutulosService(appConfig.sijoitteluContext.raportointiService, appConfig.ohjausparametritService,
    valintarekisteriDb, sijoittelunTulosRestClient)
  lazy val hakukohdeRecordService = new HakukohdeRecordService(hakuService, valintarekisteriDb, true)
  lazy val vastaanotettavuusService = new VastaanotettavuusService(hakukohdeRecordService, valintarekisteriDb)
  lazy val valintatulosService = new ValintatulosService(vastaanotettavuusService, sijoittelutulosService, valintarekisteriDb, hakuService, valintarekisteriDb, hakukohdeRecordService)(appConfig, dynamicAppConfig)
  lazy val vastaanottoService = new VastaanottoService(hakuService, hakukohdeRecordService, vastaanotettavuusService, valintatulosService,
    valintarekisteriDb, appConfig.ohjausparametritService, sijoittelutulosService, new HakemusRepository(), appConfig.sijoitteluContext.valintatulosRepository)
  lazy val valintatulokset = new ValintatulosMongoCollection(appConfig.settings.valintatulosMongoConfig)
  lazy val poller = new MailPoller(valintatulokset, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 3)
  lazy val mailDecorator = new MailDecorator(new HakemusRepository(), valintatulokset, hakuService)

  "Sähköposti lähtee, kun ehdollisesta vastaanotosta tulee sitova" in {

    "Vastaanottosähköpostit" in {
      val fixture = new GeneratedFixture(List(SimpleGeneratedHakuFixture(3, 1, "1.2.3.4.6",
        List(HakemuksenTila.VARALLA, HakemuksenTila.VARALLA, HakemuksenTila.HYVAKSYTTY))))
      fixture.apply

      val res1 = vastaanottoService.vastaanotaVirkailijana(List(VastaanottoEventDto("3.1", "1.2.3.4.6.1", "1.2.3.4.6.1", "3", "1.2.3.4.6",
        Vastaanottotila.ehdollisesti_vastaanottanut, "6.4.3.2.1", "testifixtuuri")))
      res1.head.result.status must_== 200

      val res2 = vastaanottoService.vastaanotaVirkailijana(List(VastaanottoEventDto("3.1", "1.2.3.4.6.1", "1.2.3.4.6.1", "3", "1.2.3.4.6",
        Vastaanottotila.kesken, "6.4.3.2.1", "testifixtuuri")))
      res2.head.result.status must_== 200

      val res3 = vastaanottoService.vastaanotaVirkailijana(List(VastaanottoEventDto("3.1", "1.2.3.4.6.1", "1.2.3.4.6.1", "3", "1.2.3.4.6",
        Vastaanottotila.vastaanottanut, "6.4.3.2.1", "testifixtuuri")))
      res3.head.result.status must_== 200

      val timestamp = System.currentTimeMillis()
      withFixedDateTime(threeDaysLater(timestamp)) {
        val newMailables = poller.pollForMailables()
        newMailables.size must_== 1
        val mailable = newMailables.head
        val hakukohde = mailable.hakukohteet.find(_.shouldMail).get
        hakukohde.reasonToMail must_== Some(MailReason.SITOVAN_VASTAANOTON_ILMOITUS)
      }
    }
  }

  "Sähköpostia ei lähde, kun 'kesken' tilaisesta vastaanotosta tulee sitova" in {
    "Vastaanottosähköpostit" in {
      val fixture = new GeneratedFixture(List(SimpleGeneratedHakuFixture(3, 1, "1.2.3.4.5",
        List(HakemuksenTila.VARALLA,HakemuksenTila.VARALLA,HakemuksenTila.HYVAKSYTTY))))
      fixture.apply

      val timestamp = System.currentTimeMillis()
      withFixedDateTime(timestamp) {
        val mailables: List[HakemusMailStatus] = poller.pollForMailables()
        vastaanotaAll(hk => Some(hk.vastaanottotila).filter(_ == kesken && hk.valintatila == Valintatila.hyväksytty).map(_ => vastaanottanut))(mailables)
        markMailablesSent(mailables)
        mailables.size must_== 1 //Fixturessa valmiiksi yksi Vastaanottoilmoitus

        withFixedDateTime(threeDaysLater(timestamp)) {
          val newMailables = poller.pollForMailables()
          newMailables.size must_== 0
        }
      }
    }
  }

  "Ehdollinen vastaanotto periytyy ylemmäs" in {

    "Vastaanottosähköpostit" in {
      val fixture = new GeneratedFixture(List(new SimpleGeneratedHakuFixture(3, 1, "1.2.3.4.7", List(HakemuksenTila.VARALLA,HakemuksenTila.HYLATTY,HakemuksenTila.HYVAKSYTTY))))
      fixture.apply

      val timestamp = System.currentTimeMillis()
      withFixedDateTime(timestamp) {
        val mailables: List[HakemusMailStatus] = poller.pollForMailables()
        mailables.size must_== 1
        markMailablesSent(mailables)

        vastaanotaAll(hk => Some(hk.vastaanottotila).filter(_ => hk.valintatila == Valintatila.hyväksytty).map(_ => ehdollisesti_vastaanottanut))(mailables)

        val fixture = new GeneratedFixture(List(new SimpleGeneratedHakuFixture(3, 1, "1.2.3.4.7", List(HakemuksenTila.VARALLA,HakemuksenTila.HYVAKSYTTY,HakemuksenTila.HYVAKSYTTY))))
        fixture.apply
        // This throws "Löytyi aiempi vastaanotto VastaanottoRecord(1.2.3.4.6.1,1.2.3.4.6,3,VastaanotaEhdollisesti,,2016-07-15 10:58:15.201395)"
        vastaanotaAll(hk => Some(hk.vastaanottotila).filter(_ == kesken && hk.valintatila == Valintatila.hylätty).map(_ => ehdollisesti_vastaanottanut))(mailables)

        withFixedDateTime(threeDaysLater(timestamp)) {
          val mailables = poller.pollForMailables()
          mailables.size must_== 0
          /*
          mailables.size must_== 1
          val mailable = mailables.head
          val hakukohde = mailable.hakukohteet.find(_.shouldMail).get
          hakukohde.reasonToMail must_== Some(MailReason.EHDOLLISEN_PERIYTYMISEN_ILMOITUS)
          */
        }
      }
    }
  }

  "Hakujen filtteröinti" in {
    "korkeakouluhaku -> mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()).apply
      poller.etsiHaut must_== List("1")
    }

    "2.asteen haku -> ei mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()) {
        override def hakuFixture = HakuFixtures.toinenAsteYhteishaku
      }.apply
      poller.etsiHaut must_== Nil
    }

    "Jos hakuaika ei alkanut -> ei mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()) {
        override def hakuFixture = "korkeakoulu-yhteishaku-hakuaika-tulevaisuudessa"
      }.apply
      poller.etsiHaut must_== Nil
    }

    "Jos hakukierros päättynyt -> ei mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()){
        override def ohjausparametritFixture = "hakukierros-paattynyt-2010"
      }.apply
      poller.etsiHaut must_== Nil
    }

    "Jos tulosten julkistus ei ole alkanut -> ei mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()){
        override def ohjausparametritFixture = "tuloksia-ei-viela-saa-julkaista"
      }.apply
      poller.etsiHaut must_== Nil
    }

    "Jos tulokset julkistettu -> mukaan" in {
      new GeneratedFixture(new SingleHakemusFixture()){
        override def ohjausparametritFixture = "tulokset-saa-julkaista"
      }.apply
      poller.etsiHaut must_== List("1")
    }
  }


  "Kun päässyt kaikkiin hakukohteisiin" in {
    lazy val fixture = new GeneratedFixture(new SimpleGeneratedHakuFixture(5, 5))

    "Meili lähetetään" in {
      fixture.apply
      val mailables: List[HakemusMailStatus] = poller.pollForMailables()
      mailables.size must_== 3
      mailables(0).hakukohteet.size must_== 5
      poller.pollForMailables().size must_== 2 // the rest of the mailables returned on next call
    }

  }

  private def threeDaysLater(timestamp: Long)= new DateTime(timestamp).plusDays(3).plusHours(1).getMillis

  private def vastaanotaAll(vastaanotto: (HakukohdeMailStatus => Option[Vastaanottotila]))(mailables: List[HakemusMailStatus]): Unit = {
    mailables.flatMap(hakemus => {
      val (personOid, hakemusOid) = (hakemus.hakijaOid, hakemus.hakemusOid)
      hakemus.hakukohteet.map(hakukohde => {
        vastaanotto.apply(hakukohde) match {
          case Some(action) => List(VastaanottoEventDto(
            hakukohde.valintatapajonoOid,
            personOid, hakemusOid, hakukohde.hakukohdeOid, hakemus.hakuOid, action, "", ""))
          case None => List()
        }
      })
    }).filter(_.nonEmpty).foreach(vastaanottoService.vastaanotaVirkailijana)
  }

  private def markMailablesSent(mailables: List[HakemusMailStatus]) {
    mailables
      .map { mail => LahetysKuittaus(mail.hakemusOid, mail.hakukohteet.map(_.hakukohdeOid), List("email"))}
      .foreach(valintatulokset.markAsSent(_))
  }

  "Kun hyväksytty yhteen kohteeseen ja hylätty toisessa" in {
    lazy val fixture = new GeneratedFixture(List(new GeneratedHakuFixture() {
      override def hakemukset = List(
        HakemuksenTulosFixture("H1", List(
          HakemuksenHakukohdeFixture("1", "1"),
          HakemuksenHakukohdeFixture("1", "2", List(ValintatapaJonoFixture(HakemuksenTila.HYLATTY)))
        )),
        HakemuksenTulosFixture("H2", List(
          HakemuksenHakukohdeFixture("1", "1", List(ValintatapaJonoFixture(HakemuksenTila.HYLATTY)))
        ))
      )
    }))

    "Meili lähetetään" in {
      fixture.apply
      val mailables: List[HakemusMailStatus] = poller.pollForMailables()
      mailables.map(_.hakemusOid).toSet must_== Set("H1")
    }

    "Meiliä ei lähetetä uudestaan" in {
      fixture.apply
      val timestamp = System.currentTimeMillis()
      withFixedDateTime(timestamp) {
        val mailables: List[HakemusMailStatus] = poller.pollForMailables()
        mailables.map(_.hakemusOid).toSet must_== Set("H1")
        markMailablesSent(mailables);
        withFixedDateTime(threeDaysLater(timestamp)) {
          val mailablesAfterSent: List[HakemusMailStatus] = poller.pollForMailables();
          mailablesAfterSent.size must_== 0;
        }
      }
    }

    "Hylätty tulos merkitään käsitellyksi, eikä tarkisteta uudelleen" in {
      fixture.apply
      withFixedDateTime("10.10.2014 0:00") {
        val mailables: List[HakemusMailStatus] = poller.pollForMailables()
        mailables.map(_.hakemusOid).toSet must_== Set("H1")
        markMailablesSent(mailables)
        pollForCandidates.map(_.hakemusOid) must_== Set.empty
        withFixedDateTime("11.10.2014 1:00") {
          pollForCandidates.map(_.hakemusOid) must_== Set.empty
        }
      }
    }
  }

  "Kun hakija on varalla" in {
    lazy val fixture = new GeneratedFixture(List(new GeneratedHakuFixture() {
      override def hakemukset = List(
        HakemuksenTulosFixture("H1", List(
          HakemuksenHakukohdeFixture("1", "1", List(ValintatapaJonoFixture(HakemuksenTila.VARALLA)))
        ))
      )
    }))

    "Meiliä ei lähetetä ja tilanne tarkistetaan 3:n päivän päästä uudelleen" in {
      fixture.apply
      withFixedDateTime("10.10.2014 0:00") {
        poller.pollForMailables().map(_.hakemusOid) must_== Nil
        withFixedDateTime("13.10.2014 1:00") {
          pollForCandidates.map(_.hakemusOid) must_== Set("H1")
        }
      }
    }
  }

  "Kun hakemuksen tulosta ei ole julkaistu" in {
    lazy val fixture = new GeneratedFixture(List(new GeneratedHakuFixture() {
      override def hakemukset = List(
        HakemuksenTulosFixture("H1", List(
          HakemuksenHakukohdeFixture("1", "1", List(ValintatapaJonoFixture(HakemuksenTila.HYVAKSYTTY)), julkaistavissa = false)
        ))
      )
    }))

    "Meiliä ei lähetetä" in {
      fixture.apply
      poller.pollForMailables() must_== Nil
    }

    "Hakemusta ei edes tarkisteta tarkemmin" in {
      fixture.apply
      pollForCandidates.map(_.hakemusOid) must_== Set.empty
    }
  }

  "Kun Hakemuksia on useammassa Haussa" in {
    val fixture = new GeneratedFixture(List(new SimpleGeneratedHakuFixture(1, 4, "1"), new SimpleGeneratedHakuFixture(1, 4, "2")))
    "Tuloksia haetaan molemmista" in {
      val poller = new MailPoller(valintatulokset, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 8)
      fixture.apply
      poller.pollForMailables().size must_== 4 // <- molemmista hauista tulee 2 hyväksyttyä
    }

    "Määrärajoitus koskee kaikkia Hakuja yhteensä" in {
      val poller = new MailPoller(valintatulokset, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 3)
      fixture.apply
      poller.pollForMailables().size must_== 3
    }
  }

  "Kun ensimmäisestä pollauksesta palautuu vain hylättäviä maileja" in {
    "Niin haetaan kunnes löytyy" in {
      val poller = new MailPoller(valintatulokset, valintatulosService, valintarekisteriDb, hakuService, appConfig.ohjausparametritService, limit = 1)
      useFixture("hyvaksytty-kesken-julkaistavissa-00000441372.json",
          extraFixtureNames = List("hyvaksytty-kesken-julkaistavissa.json"),
          hakemusFixtures = List("00000441369", "00000441372-no-email"))
      poller.searchMailsToSend(mailDecorator = mailDecorator).size must_== 1
    }
  }

  step(valintarekisteriDb.db.shutdown)

  private def verifyMailSent(hakemusOid: String, hakukohdeOid: String, timestamp: Long) {
    val valintatulosCollection = MongoFactory.createDB(appConfig.settings.valintatulosMongoConfig)("Valintatulos")
    val query = MongoDBObject(
      "hakemusOid" -> hakemusOid,
      "hakukohdeOid" -> hakukohdeOid
    )
    val result: DBObject = valintatulosCollection.findOne(query).get
    result.expand[java.util.Date]("mailStatus.sent") must_== Some(new java.util.Date(timestamp))
    result.expand[List[String]]("mailStatus.media") must_== Some(List("email"))
  }

  private def pollForCandidates: Set[HakemusIdentifier] = {
    valintatulokset.pollForCandidates(poller.etsiHaut, poller.limit)
  }

}
