package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{PreparedStatement, Timestamp, Types}
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatapajono, Hakemus => SijoitteluHakemus, _}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.StoreSijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

import scala.concurrent.duration.Duration
import scala.util.Try

import slick.driver.PostgresDriver.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._

trait StoreSijoitteluRepositoryImpl extends StoreSijoitteluRepository with ValintarekisteriRepository {

  override def storeSijoittelu(sijoittelu: SijoitteluWrapper): Unit = time(s"Haun ${sijoittelu.sijoitteluajo.getHakuOid} koko sijoittelun ${sijoittelu.sijoitteluajo.getSijoitteluajoId} tallennus") {
    val sijoitteluajoId = sijoittelu.sijoitteluajo.getSijoitteluajoId
    val hakuOid = sijoittelu.sijoitteluajo.getHakuOid
    runBlocking(insertSijoitteluajo(sijoittelu.sijoitteluajo)
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.map(insertHakukohde(hakuOid, _))))
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(hakukohde =>
          hakukohde.getValintatapajonot.asScala.map(insertValintatapajono(sijoitteluajoId, hakukohde.getOid, _)))))
      .andThen(SimpleDBIO { session =>
        var jonosijaStatement:Option[PreparedStatement] = None
        var pistetietoStatement:Option[PreparedStatement] = None
        var valinnantulosStatement:Option[PreparedStatement] = None
        var valinnantilaStatement:Option[PreparedStatement] = None
        var tilankuvausStatement:Option[PreparedStatement] = None
        var tilaKuvausMappingStatement:Option[PreparedStatement] = None
        try {
          jonosijaStatement = Some(createJonosijaStatement(session.connection))
          pistetietoStatement = Some(createPistetietoStatement(session.connection))
          valinnantulosStatement = Some(createValinnantulosStatement(session.connection))
          valinnantilaStatement = Some(createValinnantilaStatement(session.connection))
          tilankuvausStatement = Some(createTilankuvausStatement(session.connection))
          tilaKuvausMappingStatement = Some(createTilaKuvausMappingStatement(session.connection))
          sijoittelu.hakukohteet.foreach(hakukohde => {
            hakukohde.getValintatapajonot.asScala.foreach(valintatapajono => {
              valintatapajono.getHakemukset.asScala.foreach(hakemus => {
                storeValintatapajononHakemus(
                  hakemus,
                  sijoittelu.valintatuloksetGroupedByHakemus.get(hakemus.getHakemusOid),
                  sijoitteluajoId,
                  hakukohde.getOid,
                  valintatapajono.getOid,
                  jonosijaStatement.get,
                  pistetietoStatement.get,
                  valinnantulosStatement.get,
                  valinnantilaStatement.get,
                  tilankuvausStatement.get,
                  tilaKuvausMappingStatement.get
                )
              })
            })
          })
          time(s"Haun $hakuOid tilankuvauksien tallennus") { tilankuvausStatement.get.executeBatch() }
          time(s"Haun $hakuOid jonosijojen tallennus") { jonosijaStatement.get.executeBatch }
          time(s"Haun $hakuOid pistetietojen tallennus") { pistetietoStatement.get.executeBatch }
          time(s"Haun $hakuOid valinnantilojen tallennus") { valinnantilaStatement.get.executeBatch }
          time(s"Haun $hakuOid valinnantulosten tallennus") { valinnantulosStatement.get.executeBatch }
          time(s"Haun $hakuOid tilankuvaus-mäppäysten tallennus") { tilaKuvausMappingStatement.get.executeBatch }
        } finally {
          Try(tilankuvausStatement.foreach(_.close))
          Try(jonosijaStatement.foreach(_.close))
          Try(pistetietoStatement.foreach(_.close))
          Try(valinnantilaStatement.foreach(_.close))
          Try(valinnantulosStatement.foreach(_.close))
          Try(tilaKuvausMappingStatement.foreach(_.close))
        }
      })
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(_.getHakijaryhmat.asScala).map(insertHakijaryhma(sijoitteluajoId, _))))
      .andThen(SimpleDBIO { session =>
        var statement:Option[PreparedStatement] = None
        try {
          statement = Some(prepareInsertHakijaryhmanHakemus(session.connection))
          sijoittelu.hakukohteet.foreach(hakukohde => {
            hakukohde.getHakijaryhmat.asScala.foreach(hakijaryhma => {
              val hyvaksytyt = hakukohde.getValintatapajonot.asScala
                .flatMap(_.getHakemukset.asScala)
                .filter(_.getHyvaksyttyHakijaryhmista.contains(hakijaryhma.getOid))
                .map(_.getHakemusOid)
                .toSet
              hakijaryhma.getHakemusOid.asScala.foreach(hakemusOid => {
                insertHakijaryhmanHakemus(hakijaryhma.getOid, sijoitteluajoId, hakemusOid, hyvaksytyt.contains(hakemusOid), statement.get)
              })
            })
          })
          time(s"Haun $hakuOid hakijaryhmien hakemusten tallennus") { statement.get.executeBatch() }
        } finally {
          Try(statement.foreach(_.close))
        }
      })
      .transactionally,
      Duration(30, TimeUnit.MINUTES))
    time(s"Haun $hakuOid sijoittelun tallennuksen jälkeinen analyze") {
      runBlocking(DBIO.seq(
        sqlu"""analyze pistetiedot""",
        sqlu"""analyze jonosijat""",
        sqlu"""analyze valinnantulokset"""),
        Duration(15, TimeUnit.MINUTES))
    }
  }

  private def storeValintatapajononHakemus(hakemus: SijoitteluHakemus,
                                           valintatulos: Option[Valintatulos],
                                           sijoitteluajoId:Long,
                                           hakukohdeOid:String,
                                           valintatapajonoOid:String,
                                           jonosijaStatement: PreparedStatement,
                                           pistetietoStatement: PreparedStatement,
                                           valinnantulosStatement: PreparedStatement,
                                           valinnantilaStatement: PreparedStatement,
                                           tilankuvausStatement: PreparedStatement,
                                           tilaKuvausMappingStatement: PreparedStatement) = {
    val hakemusWrapper = SijoitteluajonHakemusWrapper(hakemus)
    createJonosijaInsertRow(sijoitteluajoId, hakukohdeOid, valintatapajonoOid, hakemusWrapper, jonosijaStatement)
    hakemus.getPistetiedot.asScala.foreach(createPistetietoInsertRow(sijoitteluajoId, valintatapajonoOid, hakemus.getHakemusOid, _, pistetietoStatement))
    createValinnantilanKuvausInsertRow(hakemusWrapper, tilankuvausStatement)
    createValinnantilaInsertRow(hakukohdeOid, valintatapajonoOid, sijoitteluajoId, hakemusWrapper, valinnantilaStatement)
    createValinnantulosInsertRow(hakemusWrapper, valintatulos, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, valinnantulosStatement)
    createTilaKuvausMappingInsertRow(hakemusWrapper, hakukohdeOid, valintatapajonoOid, tilaKuvausMappingStatement)
  }

  private def createStatement(sql:String) = (connection:java.sql.Connection) => connection.prepareStatement(sql)

  private def createJonosijaStatement = createStatement("""insert into jonosijat (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid, hakija_oid, prioriteetti,
          jonosija, varasijan_numero, onko_muuttunut_viime_sijoittelussa, pisteet, tasasijajonosija, hyvaksytty_harkinnanvaraisesti,
          siirtynyt_toisesta_valintatapajonosta, tila) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::valinnantila)""")

  private def createJonosijaInsertRow(sijoitteluajoId: Long, hakukohdeOid: String, valintatapajonoOid: String, hakemus: SijoitteluajonHakemusWrapper, statement: PreparedStatement) = {
    val SijoitteluajonHakemusWrapper(hakemusOid, hakijaOid, prioriteetti, jonosija, varasijanNumero,
    onkoMuuttunutViimeSijoittelussa, pisteet, tasasijaJonosija, hyvaksyttyHarkinnanvaraisesti, siirtynytToisestaValintatapajonosta,
    valinnantila, tilanKuvaukset, tilankuvauksenTarkenne, tarkenteenLisatieto, hyvaksyttyHakijaryhmista, _) = hakemus

    statement.setString(1, valintatapajonoOid)
    statement.setLong(2, sijoitteluajoId)
    statement.setString(3, hakukohdeOid)
    statement.setString(4, hakemusOid)
    statement.setString(5, hakijaOid.orNull)
    statement.setInt(6, prioriteetti)
    statement.setInt(7, jonosija)
    varasijanNumero match {
      case Some(x) => statement.setInt(8, x)
      case _ => statement.setNull(8, Types.INTEGER)
    }
    statement.setBoolean(9, onkoMuuttunutViimeSijoittelussa)
    statement.setBigDecimal(10, pisteet.map(_.bigDecimal).orNull)
    statement.setInt(11, tasasijaJonosija)
    statement.setBoolean(12, hyvaksyttyHarkinnanvaraisesti)
    statement.setBoolean(13, siirtynytToisestaValintatapajonosta)
    statement.setString(14, valinnantila.toString)
    statement.addBatch()
  }

  private def createPistetietoStatement = createStatement("""insert into pistetiedot (sijoitteluajo_id, hakemus_oid, valintatapajono_oid,
    tunniste, arvo, laskennallinen_arvo, osallistuminen) values (?, ?, ?, ?, ?, ?, ?)""")

  private def createPistetietoInsertRow(sijoitteluajoId: Long, valintatapajonoOid: String, hakemusOid:String, pistetieto: Pistetieto, statement: PreparedStatement) = {
    val SijoitteluajonPistetietoWrapper(tunniste, arvo, laskennallinenArvo, osallistuminen)
    = SijoitteluajonPistetietoWrapper(pistetieto)

    statement.setLong(1, sijoitteluajoId)
    statement.setString(2, hakemusOid)
    statement.setString(3, valintatapajonoOid)
    statement.setString(4, tunniste)
    statement.setString(5, arvo.orNull)
    statement.setString(6, laskennallinenArvo.orNull)
    statement.setString(7, osallistuminen.orNull)
    statement.addBatch()
  }

  private def createValinnantulosStatement = createStatement(
    """insert into valinnantulokset(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             julkaistavissa,
             hyvaksytty_varasijalta,
             ilmoittaja,
             selite
           ) values (?, ?, ?, ?, ?, ?::text, 'Sijoittelun tallennus')
           on conflict on constraint valinnantulokset_pkey do update set
             julkaistavissa = excluded.julkaistavissa,
             hyvaksytty_varasijalta = excluded.hyvaksytty_varasijalta
           where ( valinnantulokset.julkaistavissa <> excluded.julkaistavissa
             or valinnantulokset.hyvaksytty_varasijalta <> excluded.hyvaksytty_varasijalta )
             and valinnantulokset.system_time @> ?::timestamp with time zone
             and ?::valinnantila <> 'Peruuntunut'::valinnantila""")

  private def createValinnantulosInsertRow(hakemus:SijoitteluajonHakemusWrapper,
                                           valintatulos: Option[Valintatulos],
                                           sijoitteluajoId:Long,
                                           hakukohdeOid:String,
                                           valintatapajonoOid:String,
                                           valinnantulosStatement:PreparedStatement) = {

    val read = valintatulos.map(_.getRead.getTime).getOrElse(System.currentTimeMillis)

    valinnantulosStatement.setString(1, valintatapajonoOid)
    valinnantulosStatement.setString(2, hakemus.hakemusOid)
    valinnantulosStatement.setString(3, hakukohdeOid)
    valinnantulosStatement.setBoolean(4, valintatulos.exists(_.getJulkaistavissa))
    valinnantulosStatement.setBoolean(5, valintatulos.exists(_.getHyvaksyttyVarasijalta))
    valinnantulosStatement.setLong(6, sijoitteluajoId)
    valinnantulosStatement.setTimestamp(7, new java.sql.Timestamp(read))
    valinnantulosStatement.setString(8, hakemus.tila.toString)
    valinnantulosStatement.addBatch()
  }

  private def createValinnantilaStatement = createStatement(
    """insert into valinnantilat (
           hakukohde_oid,
           valintatapajono_oid,
           hakemus_oid,
           tila,
           tilan_viimeisin_muutos,
           ilmoittaja,
           henkilo_oid
       ) values (?, ?, ?, ?::valinnantila, ?, ?::text, ?)
       on conflict on constraint valinnantilat_pkey do update set
           tila = excluded.tila,
           tilan_viimeisin_muutos = excluded.tilan_viimeisin_muutos,
           ilmoittaja = excluded.ilmoittaja
       where valinnantilat.tila <> excluded.tila
    """)

  private def createValinnantilaInsertRow(hakukohdeOid: String,
                                          valintatapajonoOid: String,
                                          sijoitteluajoId: Long,
                                          hakemus: SijoitteluajonHakemusWrapper,
                                          statement: PreparedStatement) = {
    val tilanViimeisinMuutos = hakemus.tilaHistoria
      .filter(_.tila.equals(hakemus.tila))
      .map(_.luotu)
      .sortWith(_.after(_))
      .headOption.getOrElse(new Date())

    statement.setString(1, hakukohdeOid)
    statement.setString(2, valintatapajonoOid)
    statement.setString(3, hakemus.hakemusOid)
    statement.setString(4, hakemus.tila.toString)
    statement.setTimestamp(5, new Timestamp(tilanViimeisinMuutos.getTime))
    statement.setLong(6, sijoitteluajoId)
    statement.setString(7, hakemus.hakijaOid.orNull)

    statement.addBatch()
  }

  private def createTilaKuvausMappingStatement = createStatement(
    """insert into tilat_kuvaukset (
          tilankuvaus_hash,
          tarkenteen_lisatieto,
          hakukohde_oid,
          valintatapajono_oid,
          hakemus_oid) values (?, ?, ?, ?, ?)
       on conflict on constraint tilat_kuvaukset_pkey do update set
           tilankuvaus_hash = excluded.tilankuvaus_hash,
           tarkenteen_lisatieto = excluded.tarkenteen_lisatieto
       where tilat_kuvaukset.tilankuvaus_hash <> excluded.tilankuvaus_hash
    """)

  private def createTilaKuvausMappingInsertRow(hakemusWrapper: SijoitteluajonHakemusWrapper,
                                               hakukohdeOid: String,
                                               valintatapajonoOid: String,
                                               statement: PreparedStatement) = {
    statement.setInt(1, hakemusWrapper.tilankuvauksenHash)
    statement.setString(2, hakemusWrapper.tarkenteenLisatieto.orNull)
    statement.setString(3, hakukohdeOid)
    statement.setString(4, valintatapajonoOid)
    statement.setString(5, hakemusWrapper.hakemusOid)
    statement.addBatch()
  }

  private def createTilankuvausStatement = createStatement("""insert into valinnantilan_kuvaukset (hash, tilan_tarkenne, text_fi, text_sv, text_en)
      values (?, ?::valinnantilanTarkenne, ?, ?, ?) on conflict do nothing""")

  private def createValinnantilanKuvausInsertRow(h: SijoitteluajonHakemusWrapper, s: PreparedStatement) = {
    s.setInt(1, h.tilankuvauksenHash)
    s.setString(2, h.tilankuvauksetWithTarkenne("tilankuvauksenTarkenne"))
    s.setString(3, h.tilankuvauksetWithTarkenne.getOrElse("FI", null))
    s.setString(4, h.tilankuvauksetWithTarkenne.getOrElse("SV", null))
    s.setString(5, h.tilankuvauksetWithTarkenne.getOrElse("EN", null))
    s.addBatch()
  }

  private def insertSijoitteluajo(sijoitteluajo:SijoitteluAjo) = {
    val SijoitteluajoWrapper(sijoitteluajoId, hakuOid, startMils, endMils) = SijoitteluajoWrapper(sijoitteluajo)
    sqlu"""insert into sijoitteluajot (id, haku_oid, "start", "end")
             values (${sijoitteluajoId}, ${hakuOid},${new Timestamp(startMils)},${new Timestamp(endMils)})"""
  }

  private def insertHakukohde(hakuOid: String, hakukohde:Hakukohde) = {
    val SijoitteluajonHakukohdeWrapper(sijoitteluajoId, oid, kaikkiJonotSijoiteltu) = SijoitteluajonHakukohdeWrapper(hakukohde)
    sqlu"""insert into sijoitteluajon_hakukohteet (sijoitteluajo_id, haku_oid, hakukohde_oid, kaikki_jonot_sijoiteltu)
             values (${sijoitteluajoId}, ${hakuOid}, ${oid}, ${kaikkiJonotSijoiteltu})"""
  }

  private def insertValintatapajono(sijoitteluajoId:Long, hakukohdeOid:String, valintatapajono:Valintatapajono) = {
    val SijoitteluajonValintatapajonoWrapper(oid, nimi, prioriteetti, tasasijasaanto, aloituspaikat, alkuperaisetAloituspaikat,
    eiVarasijatayttoa, kaikkiEhdonTayttavatHyvaksytaan, poissaOlevaTaytto, varasijat, varasijaTayttoPaivat,
    varasijojaKaytetaanAlkaen, varasijojaTaytetaanAsti, tayttojono, alinHyvaksyttyPistemaara, valintaesitysHyvaksytty)
    = SijoitteluajonValintatapajonoWrapper(valintatapajono)

    val varasijojaKaytetaanAlkaenTs:Option[Timestamp] = varasijojaKaytetaanAlkaen.flatMap(d => Option(new Timestamp(d.getTime)))
    val varasijojaTaytetaanAstiTs:Option[Timestamp] = varasijojaTaytetaanAsti.flatMap(d => Option(new Timestamp(d.getTime)))

    sqlu"""insert into valintatapajonot (oid, sijoitteluajo_id, hakukohde_oid, nimi, prioriteetti, tasasijasaanto, aloituspaikat,
           alkuperaiset_aloituspaikat, kaikki_ehdon_tayttavat_hyvaksytaan, poissaoleva_taytto, ei_varasijatayttoa,
           varasijat, varasijatayttopaivat, varasijoja_kaytetaan_alkaen, varasijoja_taytetaan_asti, tayttojono,
           alin_hyvaksytty_pistemaara, valintaesitys_hyvaksytty)
           values (${oid}, ${sijoitteluajoId}, ${hakukohdeOid}, ${nimi}, ${prioriteetti}, ${tasasijasaanto.toString}::tasasijasaanto, ${aloituspaikat},
           ${alkuperaisetAloituspaikat}, ${kaikkiEhdonTayttavatHyvaksytaan},
           ${poissaOlevaTaytto}, ${eiVarasijatayttoa}, ${varasijat}, ${varasijaTayttoPaivat},
           ${varasijojaKaytetaanAlkaenTs}, ${varasijojaTaytetaanAstiTs}, ${tayttojono},
           ${alinHyvaksyttyPistemaara}, ${valintaesitysHyvaksytty})"""
  }

  private def insertHakijaryhma(sijoitteluajoId:Long, hakijaryhma:Hakijaryhma) = {
    val SijoitteluajonHakijaryhmaWrapper(oid, nimi, prioriteetti, kiintio, kaytaKaikki, tarkkaKiintio,
    kaytetaanRyhmaanKuuluvia, _, valintatapajonoOid, hakukohdeOid, hakijaryhmatyyppikoodiUri)
    = SijoitteluajonHakijaryhmaWrapper(hakijaryhma)

    sqlu"""insert into hakijaryhmat (oid, sijoitteluajo_id, hakukohde_oid, nimi, prioriteetti,
           kiintio, kayta_kaikki, tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia,
           valintatapajono_oid, hakijaryhmatyyppikoodi_uri)
           values (${oid}, ${sijoitteluajoId}, ${hakukohdeOid}, ${nimi}, ${prioriteetti}, ${kiintio}, ${kaytaKaikki},
      ${tarkkaKiintio}, ${kaytetaanRyhmaanKuuluvia}, ${valintatapajonoOid}, ${hakijaryhmatyyppikoodiUri})"""
  }

  private def prepareInsertHakijaryhmanHakemus(c: java.sql.Connection) = c.prepareStatement(
    """insert into hakijaryhman_hakemukset (hakijaryhma_oid, sijoitteluajo_id, hakemus_oid, hyvaksytty_hakijaryhmasta)
       values (?, ?, ?, ?)"""
  )

  private def insertHakijaryhmanHakemus(hakijaryhmaOid:String,
                                        sijoitteluajoId:Long,
                                        hakemusOid:String,
                                        hyvaksyttyHakijaryhmasta:Boolean,
                                        statement: PreparedStatement) = {
    var i = 1
    statement.setString(i, hakijaryhmaOid); i += 1
    statement.setLong(i, sijoitteluajoId); i += 1
    statement.setString(i, hakemusOid); i += 1
    statement.setBoolean(i, hyvaksyttyHakijaryhmasta); i += 1
    statement.addBatch()
  }
}
