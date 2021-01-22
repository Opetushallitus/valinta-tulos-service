package fi.vm.sade.valintatulosservice.valintarekisteri

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.Valintatapajono.JonosijaTieto
import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.sijoittelu.tulos.dto.SijoitteluajoDTO
import fi.vm.sade.valintatulosservice.json4sCustomFormats
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Tasasijasaanto, _}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods
import org.json4s.native.JsonMethods._
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import slick.dbio.DBIOAction
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.collection.immutable.IndexedSeq

trait ValintarekisteriDbTools extends Specification  with json4sCustomFormats {

  val singleConnectionValintarekisteriDb:ValintarekisteriDb

  def createTestSession(roles:Set[Role] = Set(Role.SIJOITTELU_CRUD, Role(s"${Role.SIJOITTELU_CRUD.s}_1.2.246.562.10.39804091914"))) =
    singleConnectionValintarekisteriDb.store(CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", roles)).toString

  implicit val formats = DefaultFormats ++ List(
    new NumberLongSerializer,
    new TasasijasaantoSerializer,
    new ValinnantilaSerializer,
    new DateSerializer,
    new TilankuvauksenTarkenneSerializer,
    new HakuOidSerializer,
    new HakukohdeOidSerializer,
    new ValintatapajonoOidSerializer,
    new HakemusOidSerializer
  )

  def hakijaryhmaOidsToSet(hakijaryhmaOids:Option[String]): Set[String] = {
    hakijaryhmaOids match {
      case Some(oids) if !oids.isEmpty => oids.split(",").toSet
      case _ => Set()
    }
  }

  private val deleteFromVastaanotot = DBIO.seq(
    sqlu"truncate table vastaanotot cascade",
    sqlu"delete from deleted_vastaanotot where id <> overriden_vastaanotto_deleted_id()",
    sqlu"truncate table henkiloviitteet cascade",
    sqlu"truncate table vanhat_vastaanotot cascade")

  def deleteAll(): Unit = {
    singleConnectionValintarekisteriDb.runBlocking(DBIO.seq(
      deleteFromVastaanotot,
      sqlu"truncate table valinnantilan_kuvaukset cascade",
      sqlu"truncate table hakijaryhman_hakemukset cascade",
      sqlu"truncate table hakijaryhmat cascade",
      sqlu"truncate table ilmoittautumiset cascade",
      sqlu"truncate table ilmoittautumiset_history cascade",
      sqlu"truncate table valinnantulokset cascade",
      sqlu"truncate table valinnantulokset_history cascade",
      sqlu"truncate table valinnantilat cascade",
      sqlu"truncate table valinnantilat_history cascade",
      sqlu"truncate table valintaesitykset cascade",
      sqlu"truncate table valintaesitykset_history cascade ",
      sqlu"truncate table jonosijat cascade",
      sqlu"truncate table valintatapajonot cascade",
      sqlu"truncate table sijoitteluajon_hakukohteet cascade",
      sqlu"truncate table yhden_paikan_saanto_voimassa cascade",
      sqlu"truncate table kk_tutkintoon_johtava cascade",
      sqlu"truncate table koulutuksen_alkamiskausi cascade",
      sqlu"truncate table hakukohteet cascade",
      sqlu"truncate table sijoitteluajot cascade",
      sqlu"truncate table lukuvuosimaksut cascade",
      sqlu"truncate table ehdollisen_hyvaksynnan_ehto cascade",
      sqlu"truncate table ehdollisen_hyvaksynnan_ehto_history cascade",
      sqlu"truncate table hyvaksynnan_ehto_hakukohteessa cascade",
      sqlu"truncate table hyvaksynnan_ehto_hakukohteessa_history cascade"
      ).transactionally)
  }

  def dateStringToTimestamp(str:String): Date = {
    new java.sql.Timestamp(new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(str).getTime)
  }

  def deleteVastaanotot(): Unit = {
    singleConnectionValintarekisteriDb.runBlocking(deleteFromVastaanotot)
  }

  def compareSijoitteluWrapperToDTO(wrapper:SijoitteluWrapper, dto:SijoitteluajoDTO) = {
    val simpleFormat = new SimpleDateFormat("dd-MM-yyyy")

    def format(date:java.util.Date) = date match {
      case null => null
      case x:java.util.Date => simpleFormat.format(x)
    }

    dto.getSijoitteluajoId mustEqual wrapper.sijoitteluajo.getSijoitteluajoId
    dto.getHakuOid mustEqual wrapper.sijoitteluajo.getHakuOid
    dto.getStartMils mustEqual wrapper.sijoitteluajo.getStartMils
    dto.getEndMils mustEqual wrapper.sijoitteluajo.getEndMils

    dto.getHakukohteet.size mustEqual wrapper.hakukohteet.size
    dto.getHakukohteet.asScala.toList.foreach(dhakukohde => {
      val whakukohde = wrapper.hakukohteet.find(_.getOid.equals(dhakukohde.getOid)).head
      dhakukohde.getSijoitteluajoId mustEqual wrapper.sijoitteluajo.getSijoitteluajoId
      dhakukohde.getEnsikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual null
      dhakukohde.getTarjoajaOid mustEqual whakukohde.getTarjoajaOid
      dhakukohde.isKaikkiJonotSijoiteltu mustEqual whakukohde.isKaikkiJonotSijoiteltu

      dhakukohde.getHakijaryhmat.size mustEqual whakukohde.getHakijaryhmat.size
      dhakukohde.getHakijaryhmat.asScala.toList.foreach(dhakijaryhma => {
        val whakijaryhma = whakukohde.getHakijaryhmat.asScala.toList.find(_.getOid.equals(dhakijaryhma.getOid)).head
        dhakijaryhma.getPrioriteetti mustEqual whakijaryhma.getPrioriteetti
        dhakijaryhma.getPaikat mustEqual whakijaryhma.getPaikat
        dhakijaryhma.getNimi mustEqual whakijaryhma.getNimi
        dhakijaryhma.getHakukohdeOid mustEqual whakukohde.getOid
        dhakijaryhma.getKiintio mustEqual whakijaryhma.getKiintio
        dhakijaryhma.isKaytaKaikki mustEqual whakijaryhma.isKaytaKaikki
        dhakijaryhma.isTarkkaKiintio mustEqual whakijaryhma.isTarkkaKiintio
        dhakijaryhma.isKaytetaanRyhmaanKuuluvia mustEqual whakijaryhma.isKaytetaanRyhmaanKuuluvia
        dhakijaryhma.getHakemusOid.asScala.toList.diff(whakijaryhma.getHakemusOid.asScala.toList) mustEqual List()
        dhakijaryhma.getValintatapajonoOid mustEqual whakijaryhma.getValintatapajonoOid
        dhakijaryhma.getHakijaryhmatyyppikoodiUri mustEqual whakijaryhma.getHakijaryhmatyyppikoodiUri
      })

      dhakukohde.getValintatapajonot.size mustEqual whakukohde.getValintatapajonot.size
      dhakukohde.getValintatapajonot.asScala.toList.foreach(dvalintatapajono => {
        val wvalintatapajono = whakukohde.getValintatapajonot.asScala.toList.find(_.getOid.equals(dvalintatapajono.getOid)).head
        dvalintatapajono.getAlinHyvaksyttyPistemaara mustEqual wvalintatapajono.getAlinHyvaksyttyPistemaara
        dvalintatapajono.getAlkuperaisetAloituspaikat mustEqual wvalintatapajono.getAlkuperaisetAloituspaikat
        dvalintatapajono.getAloituspaikat mustEqual wvalintatapajono.getAloituspaikat
        dvalintatapajono.getEiVarasijatayttoa mustEqual wvalintatapajono.getEiVarasijatayttoa
        dvalintatapajono.getHakeneet mustEqual wvalintatapajono.getHakemukset.size
        dvalintatapajono.getKaikkiEhdonTayttavatHyvaksytaan mustEqual wvalintatapajono.getKaikkiEhdonTayttavatHyvaksytaan
        dvalintatapajono.getNimi mustEqual wvalintatapajono.getNimi
        dvalintatapajono.getPoissaOlevaTaytto mustEqual wvalintatapajono.getPoissaOlevaTaytto
        dvalintatapajono.getPrioriteetti mustEqual wvalintatapajono.getPrioriteetti
        dvalintatapajono.getTasasijasaanto.toString mustEqual wvalintatapajono.getTasasijasaanto.toString
        dvalintatapajono.getTayttojono mustEqual wvalintatapajono.getTayttojono
        wvalintatapajono.getValintaesitysHyvaksytty match {
          case null => dvalintatapajono.getValintaesitysHyvaksytty mustEqual false
          case x => dvalintatapajono.getValintaesitysHyvaksytty mustEqual x
        }
        dvalintatapajono.getVaralla mustEqual wvalintatapajono.getVaralla
        dvalintatapajono.getVarasijat mustEqual wvalintatapajono.getVarasijat
        dvalintatapajono.getVarasijaTayttoPaivat mustEqual wvalintatapajono.getVarasijaTayttoPaivat
        format(dvalintatapajono.getVarasijojaKaytetaanAlkaen) mustEqual format(wvalintatapajono.getVarasijojaKaytetaanAlkaen)
        format(dvalintatapajono.getVarasijojaTaytetaanAsti) mustEqual format(wvalintatapajono.getVarasijojaTaytetaanAsti)

        dvalintatapajono.getHakemukset.size mustEqual wvalintatapajono.getHakemukset.size
        dvalintatapajono.getHakemukset.asScala.toList.foreach(dhakemus => {
          val whakemus = wvalintatapajono.getHakemukset.asScala.toList.find(_.getHakemusOid.equals(dhakemus.getHakemusOid)).head
          dhakemus.getHakijaOid mustEqual whakemus.getHakijaOid
          dhakemus.getHakemusOid mustEqual whakemus.getHakemusOid
          dhakemus.getPisteet mustEqual whakemus.getPisteet
          // TODO: ei datassa? dhakemus.getPaasyJaSoveltuvuusKokeenTulos mustEqual whakemus.getPaasyJaSoveltuvuusKokeenTulos
          dhakemus.getPrioriteetti mustEqual whakemus.getPrioriteetti
          dhakemus.getJonosija mustEqual whakemus.getJonosija
          dhakemus.getTasasijaJonosija mustEqual whakemus.getTasasijaJonosija
          dhakemus.getTila.toString mustEqual whakemus.getTila.toString
          dhakemus.getTilanKuvaukset mustEqual whakemus.getTilanKuvaukset
          // TODO tallennetaanko historia vanhoista? dhakemus.getTilahistoria mustEqual whakemus.getTilahistoria
          dhakemus.isHyvaksyttyHarkinnanvaraisesti mustEqual whakemus.isHyvaksyttyHarkinnanvaraisesti
          dhakemus.getVarasijanNumero mustEqual whakemus.getVarasijanNumero
          dhakemus.getValintatapajonoOid mustEqual wvalintatapajono.getOid
          dhakemus.isOnkoMuuttunutViimeSijoittelussa mustEqual whakemus.isOnkoMuuttunutViimeSijoittelussa
          dhakemus.getHyvaksyttyHakijaryhmista.asScala.diff(whakemus.getHyvaksyttyHakijaryhmista.asScala) mustEqual Set()
          dhakemus.getSiirtynytToisestaValintatapajonosta mustEqual whakemus.getSiirtynytToisestaValintatapajonosta
          //TODO: ?? dhakemus.getTodellinenJonosija mustEqual whakemus.getJonosija
        })
      })
    })
    true must beTrue
  }

  def compareSijoitteluWrapperToEntity(wrapper:SijoitteluWrapper, entity:SijoitteluAjo, hakukohteet: java.util.List[Hakukohde]) = {
    val simpleFormat = new SimpleDateFormat("dd-MM-yyyy")

    def format(date:java.util.Date) = date match {
      case null => null
      case x:java.util.Date => simpleFormat.format(x)
    }

    entity.getSijoitteluajoId mustEqual wrapper.sijoitteluajo.getSijoitteluajoId
    entity.getHakuOid mustEqual wrapper.sijoitteluajo.getHakuOid
    entity.getStartMils mustEqual wrapper.sijoitteluajo.getStartMils
    entity.getEndMils mustEqual wrapper.sijoitteluajo.getEndMils

    hakukohteet.size mustEqual wrapper.hakukohteet.size
    hakukohteet.asScala.toList.foreach(dhakukohde => {
      val whakukohde = wrapper.hakukohteet.find(_.getOid.equals(dhakukohde.getOid)).head
      dhakukohde.getSijoitteluajoId mustEqual wrapper.sijoitteluajo.getSijoitteluajoId
      //dhakukohde.getEnsikertalaisuusHakijaryhmanAlimmatHyvaksytytPisteet mustEqual null
      dhakukohde.getTarjoajaOid mustEqual whakukohde.getTarjoajaOid
      dhakukohde.isKaikkiJonotSijoiteltu mustEqual whakukohde.isKaikkiJonotSijoiteltu

      dhakukohde.getHakijaryhmat.size mustEqual whakukohde.getHakijaryhmat.size
      dhakukohde.getHakijaryhmat.asScala.toList.foreach(dhakijaryhma => {
        val whakijaryhma = whakukohde.getHakijaryhmat.asScala.toList.find(_.getOid.equals(dhakijaryhma.getOid)).head
        dhakijaryhma.getPrioriteetti mustEqual whakijaryhma.getPrioriteetti
        dhakijaryhma.getPaikat mustEqual whakijaryhma.getPaikat
        dhakijaryhma.getNimi mustEqual whakijaryhma.getNimi
        dhakijaryhma.getHakukohdeOid mustEqual whakukohde.getOid
        dhakijaryhma.getKiintio mustEqual whakijaryhma.getKiintio
        dhakijaryhma.isKaytaKaikki mustEqual whakijaryhma.isKaytaKaikki
        dhakijaryhma.isTarkkaKiintio mustEqual whakijaryhma.isTarkkaKiintio
        dhakijaryhma.isKaytetaanRyhmaanKuuluvia mustEqual whakijaryhma.isKaytetaanRyhmaanKuuluvia
        dhakijaryhma.getHakemusOid.asScala.toList.diff(whakijaryhma.getHakemusOid.asScala.toList) mustEqual List()
        dhakijaryhma.getValintatapajonoOid mustEqual whakijaryhma.getValintatapajonoOid
        dhakijaryhma.getHakijaryhmatyyppikoodiUri mustEqual whakijaryhma.getHakijaryhmatyyppikoodiUri
      })

      dhakukohde.getValintatapajonot.size mustEqual whakukohde.getValintatapajonot.size
      dhakukohde.getValintatapajonot.asScala.toList.foreach(dvalintatapajono => {
        val wvalintatapajono = whakukohde.getValintatapajonot.asScala.toList.find(_.getOid.equals(dvalintatapajono.getOid)).head
        dvalintatapajono.getAlinHyvaksyttyPistemaara mustEqual wvalintatapajono.getAlinHyvaksyttyPistemaara
        dvalintatapajono.getAlkuperaisetAloituspaikat mustEqual wvalintatapajono.getAlkuperaisetAloituspaikat
        dvalintatapajono.getAloituspaikat mustEqual wvalintatapajono.getAloituspaikat
        dvalintatapajono.getEiVarasijatayttoa mustEqual wvalintatapajono.getEiVarasijatayttoa
        dvalintatapajono.getHakemustenMaara mustEqual wvalintatapajono.getHakemukset.size
        dvalintatapajono.getKaikkiEhdonTayttavatHyvaksytaan mustEqual wvalintatapajono.getKaikkiEhdonTayttavatHyvaksytaan
        dvalintatapajono.getNimi mustEqual wvalintatapajono.getNimi
        dvalintatapajono.getPoissaOlevaTaytto mustEqual wvalintatapajono.getPoissaOlevaTaytto
        dvalintatapajono.getPrioriteetti mustEqual wvalintatapajono.getPrioriteetti
        dvalintatapajono.getTasasijasaanto.toString mustEqual wvalintatapajono.getTasasijasaanto.toString
        dvalintatapajono.getTayttojono mustEqual wvalintatapajono.getTayttojono
        wvalintatapajono.getValintaesitysHyvaksytty match {
          case null => dvalintatapajono.getValintaesitysHyvaksytty mustEqual false
          case x => dvalintatapajono.getValintaesitysHyvaksytty mustEqual x
        }
        dvalintatapajono.getVaralla mustEqual wvalintatapajono.getVaralla
        dvalintatapajono.getVarasijat mustEqual wvalintatapajono.getVarasijat
        dvalintatapajono.getVarasijaTayttoPaivat mustEqual wvalintatapajono.getVarasijaTayttoPaivat
        dvalintatapajono.getSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa mustEqual wvalintatapajono.getSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa
        format(dvalintatapajono.getVarasijojaKaytetaanAlkaen) mustEqual format(wvalintatapajono.getVarasijojaKaytetaanAlkaen)
        format(dvalintatapajono.getVarasijojaTaytetaanAsti) mustEqual format(wvalintatapajono.getVarasijojaTaytetaanAsti)
        dvalintatapajono.getSivssnovSijoittelunVarasijataytonRajoitus mustEqual wvalintatapajono.getSivssnovSijoittelunVarasijataytonRajoitus

        dvalintatapajono.getHakemukset.size mustEqual wvalintatapajono.getHakemukset.size
        dvalintatapajono.getHakemukset.asScala.toList.foreach(dhakemus => {
          val whakemus = wvalintatapajono.getHakemukset.asScala.toList.find(_.getHakemusOid.equals(dhakemus.getHakemusOid)).head
          dhakemus.getHakijaOid mustEqual whakemus.getHakijaOid
          dhakemus.getHakemusOid mustEqual whakemus.getHakemusOid
          dhakemus.getPisteet mustEqual whakemus.getPisteet
          // TODO: ei datassa? dhakemus.getPaasyJaSoveltuvuusKokeenTulos mustEqual whakemus.getPaasyJaSoveltuvuusKokeenTulos
          dhakemus.getEtunimi mustEqual whakemus.getEtunimi
          dhakemus.getSukunimi mustEqual whakemus.getSukunimi
          dhakemus.getPrioriteetti mustEqual whakemus.getPrioriteetti
          dhakemus.getJonosija mustEqual whakemus.getJonosija
          dhakemus.getTasasijaJonosija mustEqual whakemus.getTasasijaJonosija
          dhakemus.getTila.toString mustEqual whakemus.getTila.toString
          dhakemus.getTilanKuvaukset mustEqual whakemus.getTilanKuvaukset
          // TODO tallennetaanko historia vanhoista? dhakemus.getTilahistoria mustEqual whakemus.getTilahistoria
          dhakemus.isHyvaksyttyHarkinnanvaraisesti mustEqual whakemus.isHyvaksyttyHarkinnanvaraisesti
          dhakemus.getVarasijanNumero mustEqual whakemus.getVarasijanNumero
          //dhakemus.getValintatapajonoOid mustEqual wvalintatapajono.getOid
          dhakemus.isOnkoMuuttunutViimeSijoittelussa mustEqual whakemus.isOnkoMuuttunutViimeSijoittelussa
          dhakemus.getHyvaksyttyHakijaryhmista.asScala.diff(whakemus.getHyvaksyttyHakijaryhmista.asScala) mustEqual Set()
          dhakemus.getSiirtynytToisestaValintatapajonosta mustEqual whakemus.getSiirtynytToisestaValintatapajonosta
          //TODO: ?? dhakemus.getTodellinenJonosija mustEqual whakemus.getJonosija
        })
      })
    })
    true must beTrue
  }

  def insertHakukohde(hakukohdeOid: HakukohdeOid, hakuOid: HakuOid) = {
    singleConnectionValintarekisteriDb.storeHakukohde(YPSHakukohde(hakukohdeOid, hakuOid, Kevat(2015)))
  }

  def loadSijoitteluFromFixture(fixture: String, path: String = "sijoittelu/", tallennaHakukohteet: Boolean = true):SijoitteluWrapper = {
    val json = parse(getClass.getClassLoader.getResourceAsStream("fixtures/" + path + fixture + ".json"))
    SijoitteluWrapper.fromJson(json) match {
      case Some(wrapper) =>
        if (tallennaHakukohteet) {
          wrapper.hakukohteet.foreach(h => insertHakukohde(HakukohdeOid(h.getOid), HakuOid(wrapper.sijoitteluajo.getHakuOid)))
        }
        wrapper
      case None => throw new IllegalArgumentException("Could not get SijoitteluWrapper: no sijoittelus.")
    }
  }

  private implicit val getSijoitteluajoResult: GetResult[SijoitteluAjo] = GetResult(r => {
    SijoitteluajoWrapper(
      sijoitteluajoId = r.nextLong,
      hakuOid = HakuOid(r.nextString),
      startMils = r.nextTimestamp.getTime,
      endMils = r.nextTimestamp.getTime).sijoitteluajo
  })

  def findSijoitteluajo(sijoitteluajoId:Long): Option[SijoitteluAjo] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select id, haku_oid, "start", "end"
            from sijoitteluajot
            where id = ${sijoitteluajoId}""".as[SijoitteluAjo]).headOption
  }

  private implicit val getSijoitteluajonHakukohdeResult: GetResult[Hakukohde] = GetResult(r => {
    SijoitteluajonHakukohdeWrapper(
      sijoitteluajoId = r.nextLong,
      oid = HakukohdeOid(r.nextString),
      kaikkiJonotSijoiteltu = r.nextBoolean).hakukohde
  })

  def findSijoitteluajonHakukohteet(sijoitteluajoId:Long): Seq[Hakukohde] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select sijoitteluajo_id, hakukohde_oid as oid, kaikki_jonot_sijoiteltu
            from sijoitteluajon_hakukohteet
            where sijoitteluajo_id = ${sijoitteluajoId}""".as[Hakukohde])
  }

  private implicit val getSijoitteluajonValintatapajonoResult: GetResult[Valintatapajono] = GetResult(r => {
    SijoitteluajonValintatapajonoWrapper(
      oid = ValintatapajonoOid(r.nextString),
      nimi = r.nextString,
      prioriteetti = r.nextInt,
      tasasijasaanto = Tasasijasaanto(r.nextString()),
      aloituspaikat = r.nextIntOption,
      alkuperaisetAloituspaikat = r.nextIntOption,
      eiVarasijatayttoa = r.nextBoolean,
      kaikkiEhdonTayttavatHyvaksytaan = r.nextBoolean,
      poissaOlevaTaytto = r.nextBoolean,
      varasijat = r.nextIntOption,
      varasijaTayttoPaivat = r.nextIntOption,
      varasijojaKaytetaanAlkaen = r.nextTimestampOption(),
      varasijojaTaytetaanAsti = r.nextTimestampOption(),
      tayttojono = r.nextStringOption(),
      alinHyvaksyttyPistemaara = r.nextBigDecimalOption,
      valintaesitysHyvaksytty = Some(false),
      sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa = r.nextBoolean()).valintatapajono
  })

  def findHakukohteenValintatapajonot(hakukohdeOid:String): Seq[Valintatapajono] = {
    val valintatapajonot = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select oid, nimi, prioriteetti, tasasijasaanto, aloituspaikat, alkuperaiset_aloituspaikat, ei_varasijatayttoa,
            kaikki_ehdon_tayttavat_hyvaksytaan, poissaoleva_taytto,
            varasijat, varasijatayttopaivat, varasijoja_kaytetaan_alkaen, varasijoja_taytetaan_asti, tayttojono,
            alin_hyvaksytty_pistemaara, sijoiteltu_ilman_varasijasaantoja_niiden_ollessa_voimassa
            from valintatapajonot
            where hakukohde_oid = ${hakukohdeOid}""".as[Valintatapajono])
    val sivssnovSijoittelunAlimmatVarallaolijat = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select valintatapajono_oid, hakukohde_oid, jonosija, tasasijajonosija, tila, hakemusoidit
            from sivssnov_sijoittelun_varasijatayton_rajoitus where hakukohde_oid = ${hakukohdeOid}""".as[(String, String, Int, Int, String, String)])
    sivssnovSijoittelunAlimmatVarallaolijat.foreach { t =>
      valintatapajonot.find(_.getOid == t._1).foreach { jono =>
        val hakemusOids: Seq[String] = JsonMethods.parse(t._6).extract[Seq[String]]
        jono.setSivssnovSijoittelunVarasijataytonRajoitus(java.util.Optional.of(new JonosijaTieto(t._3, t._4, Valinnantila(t._5).valinnantila, hakemusOids.asJava)))
      }
    }

    valintatapajonot
  }

  private implicit val getSijoitteluajonHakijaryhmaResult = GetResult(r => {
    SijoitteluajonHakijaryhmaWrapper(oid = r.nextString,
      nimi = r.nextString,
      prioriteetti = r.nextInt,
      kiintio = r.nextInt,
      kaytaKaikki = r.nextBoolean,
      tarkkaKiintio = r.nextBoolean,
      kaytetaanRyhmaanKuuluvia = r.nextBoolean,
      hakemusOid = List(),
      valintatapajonoOid = r.nextStringOption.map(ValintatapajonoOid),
      hakukohdeOid = r.nextStringOption.map(HakukohdeOid),
      hakijaryhmatyyppikoodiUri = r.nextStringOption).hakijaryhma
  })

  def findHakukohteenHakijaryhmat(hakukohdeOid:String): Seq[Hakijaryhma] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select oid, nimi, prioriteetti, kiintio, kayta_kaikki, tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia,
            valintatapajono_oid, hakukohde_oid, hakijaryhmatyyppikoodi_uri
            from hakijaryhmat
            where hakukohde_oid = ${hakukohdeOid}""".as[Hakijaryhma]
    )
  }

  def findHakijaryhmanHakemukset(hakijaryhmaOid:String): Seq[String] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select hh.hakemus_oid
            from hakijaryhman_hakemukset hh
            inner join hakijaryhmat h ON hh.hakijaryhma_oid = h.oid and hh.sijoitteluajo_id = h.sijoitteluajo_id
            where h.oid = ${hakijaryhmaOid}""".as[String]
    )
  }

  implicit val getHakemuksetForValintatapajonosResult: GetResult[HakemusRecord] = GetResult(r => HakemusRecord(
    hakijaOid = r.nextStringOption,
    hakemusOid = HakemusOid(r.nextString),
    pisteet = r.nextBigDecimalOption,
    prioriteetti = r.nextInt,
    jonosija = r.nextInt,
    tasasijaJonosija = r.nextInt,
    tila = Valinnantila(r.nextString),
    tilankuvausHash = r.nextInt,
    tarkenteenLisatieto = r.nextStringOption,
    hyvaksyttyHarkinnanvaraisesti = r.nextBoolean,
    varasijaNumero = r.nextIntOption,
    onkoMuuttunutviimesijoittelusta = r.nextBoolean,
    siirtynytToisestaValintatapaJonosta = r.nextBoolean,
    valintatapajonoOid = ValintatapajonoOid(r.nextString)))

  private def findValintatapajononJonosijat(valintatapajonoOid:String): Seq[Hakemus] = {
    val hakemukset = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select
                vt.henkilo_oid,
                j.hakemus_oid,
                j.pisteet,
                j.prioriteetti,
                j.jonosija,
                j.tasasijajonosija,
                vt.tila,
                t_k.tilankuvaus_hash,
                t_k.tarkenteen_lisatieto,
                j.hyvaksytty_harkinnanvaraisesti,
                j.varasijan_numero,
                j.onko_muuttunut_viime_sijoittelussa,
                j.siirtynyt_toisesta_valintatapajonosta,
                j.valintatapajono_oid
            from jonosijat as j
            join valinnantulokset as v on v.valintatapajono_oid = j.valintatapajono_oid
                and v.hakemus_oid = j.hakemus_oid
                and v.hakukohde_oid = j.hakukohde_oid
            join valinnantilat as vt on vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakemus_oid = v.hakemus_oid
                and vt.hakukohde_oid = v.hakukohde_oid
            join tilat_kuvaukset t_k on v.valintatapajono_oid = t_k.valintatapajono_oid
                and v.hakemus_oid = t_k.hakemus_oid
            where j.valintatapajono_oid = ${valintatapajonoOid}
        """.as[HakemusRecord]
    ).toList
    val hakijaryhmaoidit = singleConnectionValintarekisteriDb.runBlocking(
      sql"""select distinct
               j.hakemus_oid,
               hh.hakijaryhma_oid
            from jonosijat j
            join hakijaryhman_hakemukset as hh on hh.hakemus_oid = j.hakemus_oid
            where j.valintatapajono_oid = ${valintatapajonoOid}
        """.as[(String, String)]
    ).groupBy(t => HakemusOid(t._1)).mapValues(_.map(_._2).toSet)
    val tilankuvaukset = singleConnectionValintarekisteriDb.getValinnantilanKuvaukset(hakemukset.map(_.tilankuvausHash))
    hakemukset.map(h => {
      SijoitteluajonHakemusWrapper(
        hakemusOid = h.hakemusOid,
        hakijaOid = h.hakijaOid,
        prioriteetti = h.prioriteetti,
        jonosija = h.jonosija,
        varasijanNumero = h.varasijaNumero,
        onkoMuuttunutViimeSijoittelussa = h.onkoMuuttunutviimesijoittelusta,
        pisteet = h.pisteet,
        tasasijaJonosija = h.tasasijaJonosija,
        hyvaksyttyHarkinnanvaraisesti = h.hyvaksyttyHarkinnanvaraisesti,
        siirtynytToisestaValintatapajonosta = h.siirtynytToisestaValintatapaJonosta,
        tila = h.tila,
        tilanKuvaukset = Some(tilankuvaukset(h.tilankuvausHash).tilankuvaukset(None).asScala.toMap),
        tilankuvauksenTarkenne = tilankuvaukset(h.tilankuvausHash).tilankuvauksenTarkenne,
        hyvaksyttyHakijaryhmista = hakijaryhmaoidit.getOrElse(h.hakemusOid, Set()),
        tilaHistoria = List()
      ).hakemus
    }).toSeq
  }

  case class JonosijanTilankuvauksetResult(tila:Valinnantila, tilankuvausHash:Long, tarkenteenLisatieto:Option[String])

  private implicit val findJonosijanTilaAndtilankuvauksetResult: GetResult[JonosijanTilankuvauksetResult] = GetResult(r => {
    JonosijanTilankuvauksetResult(tila = Valinnantila(r.nextString), tilankuvausHash = r.nextLong, tarkenteenLisatieto = r.nextStringOption)
  })

  private def findJonosijanTilaAndtilankuvaukset(hakemusOid:String, sijoitteluajoId:Long, valintatapajonoOid:String) = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select tila, tilankuvaus_hash, tarkenteen_lisatieto
            from jonosijat j
              left join tilat_kuvaukset t_k on t_k.hakemus_oid = j.hakemus_oid and t_k.valintatapajono_oid = j.valintatapajono_oid
            where j.hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId} and j.valintatapajono_oid = ${valintatapajonoOid}
         """.as[JonosijanTilankuvauksetResult]).head
  }

  def findTilanViimeisinMuutos(hakemusOid:String):Seq[java.sql.Timestamp] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select tilan_viimeisin_muutos from valinnantilat where hakemus_oid = ${hakemusOid}""".as[java.sql.Timestamp]
    )
  }

  def assertSijoittelu(wrapper:SijoitteluWrapper): MatchResult[Any] = {
    val stored: Option[SijoitteluAjo] = findSijoitteluajo(wrapper.sijoitteluajo.getSijoitteluajoId)
    stored.isDefined must beTrue
    SijoitteluajoWrapper(stored.get) mustEqual SijoitteluajoWrapper(wrapper.sijoitteluajo)
    val storedHakukohteet: Seq[Hakukohde] = findSijoitteluajonHakukohteet(stored.get.getSijoitteluajoId)
    wrapper.hakukohteet.foreach(hakukohde => {
      val storedHakukohde = storedHakukohteet.find(_.getOid.equals(hakukohde.getOid))
      storedHakukohde.isDefined must beTrue
      SijoitteluajonHakukohdeWrapper(hakukohde) mustEqual SijoitteluajonHakukohdeWrapper(storedHakukohde.get)
      val storedValintatapajonot = findHakukohteenValintatapajonot(hakukohde.getOid)
      import scala.collection.JavaConverters._
      hakukohde.getValintatapajonot.asScala.toList.foreach(valintatapajono => {
        val storedValintatapajono = storedValintatapajonot.find(_.getOid.equals(valintatapajono.getOid))
        storedValintatapajono.isDefined must beTrue
        storedValintatapajono.get.getSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa mustEqual valintatapajono.getSijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa
        SijoitteluajonValintatapajonoWrapper(valintatapajono) mustEqual SijoitteluajonValintatapajonoWrapper(storedValintatapajono.get)
        val storedJonosijat = findValintatapajononJonosijat(valintatapajono.getOid)
        valintatapajono.getHakemukset.asScala.toList.foreach(hakemus => {
          val storedJonosija = storedJonosijat.find(_.getHakemusOid.equals(hakemus.getHakemusOid))
          storedJonosija.isDefined must beTrue
          val jonosijaWrapper = SijoitteluajonHakemusWrapper(storedJonosija.get)
          val hakemusWrapper = SijoitteluajonHakemusWrapper(hakemus).copy(tilaHistoria = List())
          hakemusWrapper mustEqual jonosijaWrapper

          val jonosijanTilankuvaukset = findJonosijanTilaAndtilankuvaukset(hakemus.getHakemusOid, wrapper.sijoitteluajo.getSijoitteluajoId, valintatapajono.getOid)
          hakemusWrapper.tila mustEqual jonosijanTilankuvaukset.tila
          hakemus.getTilanKuvaukset.hashCode() mustEqual jonosijanTilankuvaukset.tilankuvausHash
        })
      })
      val storedHakijaryhmat = findHakukohteenHakijaryhmat(hakukohde.getOid)
      storedHakijaryhmat.length mustEqual hakukohde.getHakijaryhmat.size
      hakukohde.getHakijaryhmat.asScala.toList.foreach(hakijaryhma => {
        val storedHakijaryhma = storedHakijaryhmat.find(_.getOid.equals(hakijaryhma.getOid))
        storedHakijaryhma.isDefined must beTrue
        storedHakijaryhma.get.getHakemusOid.addAll(findHakijaryhmanHakemukset(hakijaryhma.getOid).asJava)
        def createSortedHakijaryhmaWrapper(hakijaryhma:Hakijaryhma) = {
          java.util.Collections.sort(hakijaryhma.getHakemusOid)
          SijoitteluajonHakijaryhmaWrapper(hakijaryhma)
        }
        createSortedHakijaryhmaWrapper(hakijaryhma) mustEqual createSortedHakijaryhmaWrapper(storedHakijaryhma.get)
      })
      storedValintatapajonot.length mustEqual hakukohde.getValintatapajonot.size
    })
    storedHakukohteet.length mustEqual wrapper.hakukohteet.length
  }

  def createHugeSijoittelu(sijoitteluajoId: Long, hakuOid: HakuOid, size: Int = 50, insertHakukohteet:Boolean = true): SijoitteluWrapper = {
    val sijoitteluajo = SijoitteluajoWrapper(sijoitteluajoId, hakuOid, System.currentTimeMillis(), System.currentTimeMillis())
    var valinnantulokset:IndexedSeq[SijoitteluajonValinnantulosWrapper] = IndexedSeq()
    val hakukohteet = (1 to size par).map(i => {
      val hakukohdeOid = HakukohdeOid(hakuOid.toString + "." + i)
      val hakukohde = SijoitteluajonHakukohdeWrapper(sijoitteluajoId, hakukohdeOid, true).hakukohde
      hakukohde.setValintatapajonot(
      (1 to 4 par).map( k => {
        val valintatapajonoOid = ValintatapajonoOid(hakukohdeOid.toString + "." + k)
        val valintatapajono = SijoitteluajonValintatapajonoWrapper( valintatapajonoOid, "nimi" + k, k, Arvonta, Some(k), Some(k), false, false,
          false, Some(k), Some(k), Some(new Date(System.currentTimeMillis)), Some(new Date(System.currentTimeMillis)),
          None, Some(k), Some(false)).valintatapajono
        valintatapajono.getHakemukset.addAll(
          (1 to size par).map( j => {
            val hakemusOid = HakemusOid(valintatapajonoOid.toString + "." + j)
            val hakemus = SijoitteluajonHakemusWrapper(hakemusOid, Some(hakemusOid.toString),
              j, j, None, false, Some(j), j, false, false, Hylatty, Some(Map("FI" -> ("fi" + j), "SV" -> ("sv" + j), "EN" -> ("en" + j))),
              EiTilankuvauksenTarkennetta, Set(""), List()).hakemus
            valinnantulokset = valinnantulokset ++ IndexedSeq(SijoitteluajonValinnantulosWrapper(valintatapajonoOid, hakemusOid, hakukohdeOid,
              false, false, false, false, None, None, MailStatusWrapper(None, None, None, None).status))
            hakemus
          }).seq.asJava
        )
        valintatapajono
      }).seq.asJava
      )
      hakukohde.setHakijaryhmat(
        (1 to 4).map( j => {
          val hakijaryhmaOid = hakukohdeOid + "." + j
          SijoitteluajonHakijaryhmaWrapper(hakijaryhmaOid, "nimi" + j, j, j, false, false, false,
            hakukohde.getValintatapajonot.get(0).getHakemukset.asScala.map(_.getHakemusOid).toList, None,
            Some(hakukohdeOid), Some("myUri" + j)).hakijaryhma
        }).asJava
      )
      val hakijaryhmaOids = hakukohde.getHakijaryhmat.asScala.map(_.getOid).toSet.asJava
      hakukohde.getValintatapajonot.get(0).getHakemukset().asScala.foreach(h => h.setHyvaksyttyHakijaryhmista(hakijaryhmaOids))
      hakukohde
    })
    if(insertHakukohteet) hakukohteet.foreach(h => insertHakukohde(HakukohdeOid(h.getOid), hakuOid))
    SijoitteluWrapper(sijoitteluajo.sijoitteluajo, hakukohteet.seq.asJava, valinnantulokset.map(_.valintatulos).asJava)
  }
}
