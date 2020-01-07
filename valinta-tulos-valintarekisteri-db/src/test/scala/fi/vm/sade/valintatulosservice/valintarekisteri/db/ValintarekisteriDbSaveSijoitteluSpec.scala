package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.HakemuksenTila.HYVAKSYTTY
import fi.vm.sade.sijoittelu.domain.Valintatapajono.JonosijaTieto
import fi.vm.sade.sijoittelu.domain.{HakemuksenTila, Valintatapajono}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.logging.PerformanceLogger
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.HyvaksynnanEhto
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterExample
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api.actionBasedSQLInterpolation

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbSaveSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterExample
  with Logging with PerformanceLogger {
  sequential
  private val hakuOid = "1.2.246.561.29.00000000001"

  val now = System.currentTimeMillis
  val nowDatetime = new Timestamp(1)

  step(appConfig.start)
  step(deleteAll())

  "ValintarekisteriDb" should {
    "store sijoitteluajoWrapper fixture" in {
      val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
    }
    "store sijoitteluajoWrapper fixture with hakijaryhmä and pistetiedot" in {
      val wrapper = loadSijoitteluFromFixture("valintatapajono_hakijaryhma_pistetiedot")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
    }
    "store tilan_viimeisin_muutos correctly" in {
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

      def assertTilanViimeisinMuutos(hakemusOid:String, expected:java.util.Date) = {
        val muutokset = findTilanViimeisinMuutos(hakemusOid)
        muutokset.size mustEqual 1
        logger.info(s"Got date:${dateFormat.format(new java.util.Date(muutokset.head.getTime))}")
        logger.info(s"Expecting date:${dateFormat.format(expected)}")
        muutokset.head.getTime mustEqual expected.getTime
      }

      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      assertSijoittelu(wrapper)
      assertTilanViimeisinMuutos("1.2.246.562.11.00006465296", dateFormat.parse("2016-10-17T09:08:11.967+0000"))
      assertTilanViimeisinMuutos("1.2.246.562.11.00004685599", dateFormat.parse("2016-10-12T04:11:20.526+0000"))
    }
  }
  implicit val resultAsObjectMap = GetResult[Map[String,Object]] (
    prs => (1 to prs.numColumns).map(_ => prs.rs.getMetaData.getColumnName(prs.currentPos+1) -> prs.nextString ).toMap )
  val hakemusOid = "1.2.246.562.11.00006926939"
  def readTable(table:String) = singleConnectionValintarekisteriDb.runBlocking(
    sql"""select * from #${table} where hakemus_oid = ${hakemusOid}""".as[Map[String,Object]])

  "Valintarekisteri" should {
    "store valinnantulos history correctly" in {
      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)

      readTable("valinnantulokset_history").size mustEqual 0

      val original = readTable("valinnantulokset").head
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantulokset
               set julkaistavissa = true
               where hakemus_oid = ${hakemusOid}""")

      val updated = readTable("valinnantulokset").head
      val history = readTable("valinnantulokset_history").head

      history.filterKeys(_ != "system_time") mustEqual original.filterKeys(_ != "system_time")

      history("system_time").asInstanceOf[String] mustEqual
        original("system_time").asInstanceOf[String].replace(")", "") +
        updated("system_time").asInstanceOf[String].replace("[", "").replace(",", "")
    }
    "store valinnantila history correctly" in {
      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)

      readTable("valinnantilat_history").size mustEqual 0

      val original = readTable("valinnantilat").head
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantilat
               set tila = 'Hylatty'
               where hakemus_oid = ${hakemusOid}""")

      val updated = readTable("valinnantilat").head
      val history = readTable("valinnantilat_history").head

      history.filterKeys(_ != "system_time") mustEqual original.filterKeys(_ != "system_time")

      history("system_time").asInstanceOf[String] mustEqual
        original("system_time").asInstanceOf[String].replace(")", "") +
          updated("system_time").asInstanceOf[String].replace("[", "").replace(",", "")
    }
    "store valintatulokset correctly" in {
      import scala.collection.JavaConverters._

      val oldDate = new Date()

      def readJulkaistavissa() = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select julkaistavissa from valinnantulokset where hakemus_oid = '1.2.246.562.11.00000441369' order by valintatapajono_oid desc""".as[Boolean]).toList

      def startNewSijoittelu(wrapper:SijoitteluWrapper) = {
        val sijoitteluajoId = System.currentTimeMillis
        wrapper.sijoitteluajo.setStartMils(System.currentTimeMillis)
        wrapper.sijoitteluajo.setSijoitteluajoId(sijoitteluajoId)
        wrapper.hakukohteet.foreach(_.setSijoitteluajoId(sijoitteluajoId))
        wrapper.sijoitteluajo.setEndMils(System.currentTimeMillis)
      }

      val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      //Päivitetään julkaistavissa vain valintatuloksen jonolle
      List(true, false) mustEqual readJulkaistavissa()

      startNewSijoittelu(wrapper)
      wrapper.valintatulokset.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(vt => {
        vt.setJulkaistavissa(false, "", "")
        vt.setRead(oldDate)
      })
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      //Ei päivitetä täppää, koska se "on muuttunut" sijoittelun aikana
      List(true, false) mustEqual readJulkaistavissa()

      startNewSijoittelu(wrapper)
      wrapper.hakukohteet.head.getValintatapajonot.asScala.find(_.getOid.equals("14090336922663576781797489829887")
      ).get.getHakemukset.asScala.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(h => {
        h.setTila(HakemuksenTila.PERUUNTUNUT)
      })
      wrapper.valintatulokset.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(vt => {
        vt.setJulkaistavissa(false, "", "")
        vt.setRead(new Date())
      })
      //Ei päivitetä täppää, koska tila on peruuntunut
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      List(true, false).diff(readJulkaistavissa()) mustEqual List()

      startNewSijoittelu(wrapper)
      wrapper.hakukohteet.head.getValintatapajonot.asScala.find(_.getOid.equals("14090336922663576781797489829887")
      ).get.getHakemukset.asScala.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(h => {
        h.setTila(HYVAKSYTTY)
      })
      wrapper.valintatulokset.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(vt => {
        vt.setJulkaistavissa(false, "", "")
        vt.setRead(new Date())
      })
      //Päivitetään täppä
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      List(false, false).diff(readJulkaistavissa()) mustEqual List()

      startNewSijoittelu(wrapper)
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantulokset
               set hyvaksy_peruuntunut = true
               where hakemus_oid = '1.2.246.562.11.00000441369' and
                     valintatapajono_oid = '14090336922663576781797489829887'""")
      wrapper.hakukohteet.head.getValintatapajonot.asScala.find(_.getOid.equals("14090336922663576781797489829887")
      ).get.getHakemukset.asScala.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(h => {
        h.setTila(HYVAKSYTTY)
      })
      wrapper.valintatulokset.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(vt => {
        vt.setJulkaistavissa(false, "", "")
        vt.setHyvaksyPeruuntunut(true, "", "")
        vt.setRead(new Date())
      })
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      // Päivitä hyväksy peruuntunut täppä aina false:ksi
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select hyvaksy_peruuntunut from valinnantulokset where hakemus_oid = '1.2.246.562.11.00000441369'""".as[Boolean]
      ).toList mustEqual List(false, false)

      startNewSijoittelu(wrapper)
      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantulokset
               set hyvaksytty_varasijalta = true
               where hakemus_oid = '1.2.246.562.11.00000441369' and
                     valintatapajono_oid = '14090336922663576781797489829887'""")
      wrapper.hakukohteet.head.getValintatapajonot.asScala.find(_.getOid.equals("14090336922663576781797489829887")
      ).get.getHakemukset.asScala.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(h => {
        h.setTila(HYVAKSYTTY)
      })
      wrapper.valintatulokset.find(_.getHakemusOid.equals("1.2.246.562.11.00000441369")).foreach(vt => {
        vt.setJulkaistavissa(false, "", "")
        vt.setHyvaksyttyVarasijalta(true, "", "")
        vt.setRead(new Date())
      })
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      // Päivitä hyväksytty varasijalta täppä aina false:ksi
      singleConnectionValintarekisteriDb.runBlocking(
        sql"""select hyvaksytty_varasijalta from valinnantulokset where hakemus_oid = '1.2.246.562.11.00000441369'""".as[Boolean]
      ).toList mustEqual List(false, false)
    }
    "not update existing julkaistavissa if valintatulos not updated in sijoitteluajo" in {
      val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)

      readTable("valinnantulokset_history").size mustEqual 0
      readTable("valinnantulokset").size mustEqual 1

      singleConnectionValintarekisteriDb.runBlocking(
        sqlu"""update valinnantulokset
               set julkaistavissa = true, hyvaksytty_varasijalta = true
               where hakemus_oid = ${hakemusOid}""")
      readTable("valinnantulokset_history").size mustEqual 1

      val newSijoitteluajoWrapper = wrapper.copy(valintatulokset = Nil)
      incrementSijoitteluajoId(newSijoitteluajoWrapper)
      singleConnectionValintarekisteriDb.storeSijoittelu(newSijoitteluajoWrapper)

      val flagsFromDb = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select julkaistavissa, hyvaksytty_varasijalta
              from valinnantulokset where hakemus_oid = ${hakemusOid}""".as[(Boolean, Boolean)])
      flagsFromDb must haveSize(1)
      flagsFromDb.head mustEqual (true, false)

      readTable("valinnantulokset_history").size mustEqual 2
      readTable("valinnantulokset").size mustEqual 1

      incrementSijoitteluajoId(newSijoitteluajoWrapper)
      singleConnectionValintarekisteriDb.storeSijoittelu(newSijoitteluajoWrapper)
      val flagsFromDbAfterSecondSave = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select julkaistavissa, hyvaksytty_varasijalta
              from valinnantulokset where hakemus_oid = ${hakemusOid}""".as[(Boolean, Boolean)])

      flagsFromDbAfterSecondSave must haveSize(1)
      flagsFromDbAfterSecondSave.head mustEqual (true, false)
      readTable("valinnantulokset_history").size mustEqual 2
      readTable("valinnantulokset").size mustEqual 1
    }
    "handle hyväksytty ja julkaistu -dates correctly" in {
      def readHyvaksyttyJaJulkaistuTable() = singleConnectionValintarekisteriDb.runBlocking(
        sql"""select * from hyvaksytyt_ja_julkaistut_hakutoiveet""".as[Map[String,Object]])

      def validate(hyvaksyttyJaJulkaistu:Vector[Map[String,Object]]) = {
        hyvaksyttyJaJulkaistu.map(_("henkilo")).distinct.size must_== 2
        hyvaksyttyJaJulkaistu.foreach(m => m("henkilo") match {
          case "1.2.246.562.24.14229104473" => m("hakukohde") must_== "1.2.246.562.5.72607738933"
          case "1.2.246.562.24.14229104472" => m("hakukohde") must_== "1.2.246.562.5.72607738922"
          case x => throw new AssertionError(s"Tuntematon henkilö oid ${x}")
        })
        val pvm = hyvaksyttyJaJulkaistu.map(_("hyvaksytty_ja_julkaistu")).distinct
        pvm.size must_== 1
        pvm.head
      }

      //Hakijat hyväksytty + julkaistu eri hakukohteista
      val wrapper = loadSijoitteluFromFixture("hyvaksytty-julkaistu-pvm-1", "sijoittelu/")
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      val pvm = validate(readHyvaksyttyJaJulkaistuTable())

      //Julkaistavissa-täpän poistaminen ei poista päivämääriä
      wrapper.valintatulokset.foreach(_.setJulkaistavissa(false, "", ""))
      incrementSijoitteluajoId(wrapper)
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      validate(readHyvaksyttyJaJulkaistuTable()) must_== pvm

      //Hakijalta 1.2.246.562.24.14229104472 poistetaan yksi hyväksyntä -> päivämäärä ei poistu, koska hyväksyntä toisessa jonossa
      //Hakija 1.2.246.562.24.14229104473 tulee hylätyksi vanhassa ja hyväksytyksi uudessa hakukohteessa -> vanha pvm poistuu, uusi tulee tilalle
      val wrapper2 = loadSijoitteluFromFixture("hyvaksytty-julkaistu-pvm-2", "sijoittelu/", tallennaHakukohteet = false)
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper2)
      val hyvaksyttyJaJulkaistu = readHyvaksyttyJaJulkaistuTable()
      hyvaksyttyJaJulkaistu.foreach(m => m("henkilo") match {
        case "1.2.246.562.24.14229104473" => {
          m("hakukohde") must_== "1.2.246.562.5.72607738922"
          m("hyvaksytty_ja_julkaistu") must_!= pvm
        }
        case "1.2.246.562.24.14229104472" => {
          m("hakukohde") must_== "1.2.246.562.5.72607738922"
          m("hyvaksytty_ja_julkaistu") must_== pvm
        }
        case x => throw new AssertionError(s"Tuntematon henkilö oid ${x}")
      })
      hyvaksyttyJaJulkaistu.map(_("henkilo")).distinct.size must_== 2
    }
    "set ehdollisesti hyväksyttävissä" in {
      val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
      val valintatapajonoOid = ValintatapajonoOid("14090336922663576781797489829887")
      val hakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
      val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          HyvaksynnanEhto("muu", "muu", "andra", "other"),
          "1.2.246.562.24.00000000002"))
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      val valinnantulos = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))
        .find(v => v.hakukohdeOid == hakukohdeOid && v.valintatapajonoOid == valintatapajonoOid)
      valinnantulos.flatMap(_.ehdollisestiHyvaksyttavissa) must beSome(true)
      valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoKoodi) must beSome("muu")
      valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoFI) must beSome("muu")
      valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoSV) must beSome("andra")
      valinnantulos.flatMap(_.ehdollisenHyvaksymisenEhtoEN) must beSome("other")
    }
    "not set ehdollisesti hyväksyttävissä if valinnantulos present" in {
      val hakemusOid = HakemusOid("1.2.246.562.11.00000441369")
      val valintatapajonoOid = ValintatapajonoOid("14090336922663576781797489829887")
      val hakukohdeOid = HakukohdeOid("1.2.246.562.5.72607738902")
      val wrapper = loadSijoitteluFromFixture("hyvaksytty-korkeakoulu-erillishaku")
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          HyvaksynnanEhto("muu", "muu", "andra", "other"),
          "1.2.246.562.24.00000000002"))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeValinnantila(
        ValinnantilanTallennus(
          hakemusOid,
          valintatapajonoOid,
          hakukohdeOid,
          "1.2.246.562.24.14229104472",
          Hylatty,
          "muokkaaja"),
        None))
      singleConnectionValintarekisteriDb.runBlocking(singleConnectionValintarekisteriDb.storeValinnantuloksenOhjaus(
        ValinnantuloksenOhjaus(
          hakemusOid,
          valintatapajonoOid,
          hakukohdeOid,
          false,
          false,
          false,
          "muokkaaja",
          "selite"),
        None))
      singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
      val valinnantulokset = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.getValinnantuloksetForHakemus(hakemusOid))

      val eiEhdollisestiHyvaksyttavissa = valinnantulokset
        .find(v => v.hakukohdeOid == hakukohdeOid && v.valintatapajonoOid == valintatapajonoOid)
      eiEhdollisestiHyvaksyttavissa.flatMap(_.ehdollisestiHyvaksyttavissa) must beSome(false)
      eiEhdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoKoodi) must beNone
      eiEhdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoFI) must beNone
      eiEhdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoSV) must beNone
      eiEhdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoEN) must beNone

      val ehdollisestiHyvaksyttavissa = valinnantulokset
        .find(v => v.hakukohdeOid == hakukohdeOid && v.valintatapajonoOid == ValintatapajonoOid("14090336922663576781797489829886"))
      ehdollisestiHyvaksyttavissa.flatMap(_.ehdollisestiHyvaksyttavissa) must beSome(true)
      ehdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoKoodi) must beSome("muu")
      ehdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoFI) must beSome("muu")
      ehdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoSV) must beSome("andra")
      ehdollisestiHyvaksyttavissa.flatMap(_.ehdollisenHyvaksymisenEhtoEN) must beSome("other")
    }
  }
  "store sijoiteltu ilman varasijasääntöjä niiden ollessa voimassa flag by valintatapajono" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    wrapper.hakukohteet.head.getValintatapajonot.get(0).setSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa(true)
    singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
    assertSijoittelu(wrapper)
  }
  "store sivssnov-sijoittelu cutoff point in valintatapajono" in {
    val wrapper = loadSijoitteluFromFixture("haku-1.2.246.562.29.75203638285", "QA-import/")
    val jono: Valintatapajono = wrapper.hakukohteet.head.getValintatapajonot.get(0)
    jono.setSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa(true)
    val hakemusOids = jono.getHakemukset.asScala.map(_.getHakemusOid).asJava
    jono.setSivssnovSijoittelunVarasijataytonRajoitus(java.util.Optional.of(new JonosijaTieto(79, 2, HYVAKSYTTY, hakemusOids)))
    singleConnectionValintarekisteriDb.storeSijoittelu(wrapper)
    assertSijoittelu(wrapper)
  }

  private def incrementSijoitteluajoId(newSijoitteluajoWrapper: SijoitteluWrapper) = {
    val newSijoitteluajoId = newSijoitteluajoWrapper.sijoitteluajo.getSijoitteluajoId + 1
    newSijoitteluajoWrapper.sijoitteluajo.setSijoitteluajoId(newSijoitteluajoId)
    newSijoitteluajoWrapper.hakukohteet.foreach(_.setSijoitteluajoId(newSijoitteluajoId))
  }

  override protected def before: Unit = {
    deleteAll()
  }
  override protected def after: Unit = {
    deleteAll()
  }

  step(deleteAll())
}
