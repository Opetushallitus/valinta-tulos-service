package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{PreparedStatement, SQLTimeoutException, Timestamp, Types}
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatapajono, Hakemus => SijoitteluHakemus, _}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.StoreSijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.driver.PostgresDriver.api._

import scala.collection.JavaConverters._
import scala.concurrent.TimeoutException
import scala.concurrent.duration.Duration
import scala.util.Try

trait StoreSijoitteluRepositoryImpl extends StoreSijoitteluRepository with ValintarekisteriRepository {

  override def storeSijoittelu(sijoittelu: SijoitteluWrapper): Unit = timed(s"Haun ${sijoittelu.sijoitteluajo.getHakuOid} koko sijoittelun ${sijoittelu.sijoitteluajo.getSijoitteluajoId} tallennus", 100) {
    val sijoitteluajoId = sijoittelu.sijoitteluajo.getSijoitteluajoId
    val hakuOid = HakuOid(sijoittelu.sijoitteluajo.getHakuOid)
    runBlocking(insertSijoitteluajo(sijoittelu.sijoitteluajo)
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.map(insertHakukohde(hakuOid, _))))
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(hakukohde =>
          hakukohde.getValintatapajonot.asScala.map(insertValintatapajono(sijoitteluajoId, HakukohdeOid(hakukohde.getOid), _)))))
      .andThen(SimpleDBIO { session =>
        var jonosijaStatement:Option[PreparedStatement] = None
        var pistetietoStatement:Option[PreparedStatement] = None
        var insertValinnantulosStatement:Option[PreparedStatement] = None
        var updateValinnantulosStatement:Option[PreparedStatement] = None
        var valinnantilaStatement:Option[PreparedStatement] = None
        var tilankuvausStatement:Option[PreparedStatement] = None
        var tilaKuvausMappingStatement:Option[PreparedStatement] = None
        var deleteHyvaksyttyJulkaistuStatement:Option[PreparedStatement] = None
        var insertHyvaksyttyJulkaistuStatement:Option[PreparedStatement] = None
        try {
          jonosijaStatement = Some(createJonosijaStatement(session.connection))
          pistetietoStatement = Some(createPistetietoStatement(session.connection))
          insertValinnantulosStatement = Some(createInsertValinnantulosStatement(session.connection))
          updateValinnantulosStatement = Some(createUpdateValinnantulosStatement(session.connection))
          valinnantilaStatement = Some(createValinnantilaStatement(session.connection))
          tilankuvausStatement = Some(createTilankuvausStatement(session.connection))
          tilaKuvausMappingStatement = Some(createTilaKuvausMappingStatement(session.connection))
          deleteHyvaksyttyJulkaistuStatement = Some(createHyvaksyttyJaJulkaistuDeleteStatement(session.connection))
          insertHyvaksyttyJulkaistuStatement = Some(createHyvaksyttyJaJulkaistuInsertStatement(session.connection))
          sijoittelu.hakukohteet.foreach(hakukohde => {
            val hakukohdeOid = HakukohdeOid(hakukohde.getOid)
            hakukohde.getValintatapajonot.asScala.foreach(valintatapajono => {
              valintatapajono.getHakemukset.asScala.foreach(hakemus => {
                val valintatapajonoOid = ValintatapajonoOid(valintatapajono.getOid)
                val hakemusOid = HakemusOid(hakemus.getHakemusOid)
                storeValintatapajononHakemus(
                  hakemus,
                  sijoittelu.groupedValintatulokset.get((hakukohdeOid, valintatapajonoOid, hakemusOid)),
                  sijoitteluajoId,
                  hakukohdeOid,
                  valintatapajonoOid,
                  jonosijaStatement.get,
                  pistetietoStatement.get,
                  insertValinnantulosStatement.get,
                  updateValinnantulosStatement.get,
                  valinnantilaStatement.get,
                  tilankuvausStatement.get,
                  tilaKuvausMappingStatement.get
                )
              })
            })
            createHyvaksyttyJaJulkaistuDeleteRow(hakukohdeOid, deleteHyvaksyttyJulkaistuStatement.get)
            createHyvaksyttyJaJulkaistuInsertRow(hakukohdeOid, sijoitteluajoId, insertHyvaksyttyJulkaistuStatement.get)
          })
          timed(s"Haun $hakuOid tilankuvauksien tallennus", 100) { tilankuvausStatement.get.executeBatch() }
          timed(s"Haun $hakuOid jonosijojen tallennus", 100) { jonosijaStatement.get.executeBatch }
          timed(s"Haun $hakuOid pistetietojen tallennus", 100) { pistetietoStatement.get.executeBatch }
          timed(s"Haun $hakuOid valinnantilojen tallennus", 100) { valinnantilaStatement.get.executeBatch }
          timed(s"Haun $hakuOid uusien valinnantulosten tallennus", 100) { insertValinnantulosStatement.get.executeBatch }
          timed(s"Haun $hakuOid päivittyneiden valinnantulosten tallennus", 100) { updateValinnantulosStatement.get.executeBatch }
          timed(s"Haun $hakuOid tilankuvaus-mäppäysten tallennus", 100) { tilaKuvausMappingStatement.get.executeBatch }
          timed(s"Haun $hakuOid hyväksytty ja julkaistu -päivämäärien poisto", 100) { deleteHyvaksyttyJulkaistuStatement.get.executeBatch }
          timed(s"Haun $hakuOid hyväksytty ja julkaistu -päivämäärien tallennus", 100) { insertHyvaksyttyJulkaistuStatement.get.executeBatch }
        } finally {
          Try(tilankuvausStatement.foreach(_.close))
          Try(jonosijaStatement.foreach(_.close))
          Try(pistetietoStatement.foreach(_.close))
          Try(valinnantilaStatement.foreach(_.close))
          Try(insertValinnantulosStatement.foreach(_.close))
          Try(updateValinnantulosStatement.foreach(_.close))
          Try(tilaKuvausMappingStatement.foreach(_.close))
          Try(deleteHyvaksyttyJulkaistuStatement.foreach(_.close))
          Try(insertHyvaksyttyJulkaistuStatement.foreach(_.close))
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
                .map(h => HakemusOid(h.getHakemusOid))
                .toSet
              hakijaryhma.getHakemusOid.asScala
                .map(HakemusOid)
                .foreach(hakemusOid => {
                  insertHakijaryhmanHakemus(hakijaryhma.getOid, sijoitteluajoId, hakemusOid, hyvaksytyt.contains(hakemusOid), statement.get)
                })
            })
          })
          timed(s"Haun $hakuOid hakijaryhmien hakemusten tallennus", 100) { statement.get.executeBatch() }
        } finally {
          Try(statement.foreach(_.close))
        }
      })
      .transactionally,
      Duration(60, TimeUnit.MINUTES))

    try {
      timed(s"Haun $hakuOid sijoittelun tallennuksen jälkeinen analyze", 100) {
        runBlocking(DBIO.seq(
          sqlu"""ANALYZE pistetiedot""",
          sqlu"""ANALYZE jonosijat""",
          sqlu"""ANALYZE valinnantulokset"""),
          Duration(20, TimeUnit.MINUTES))
      }
    } catch {
      case te:TimeoutException => logger.warn(s"Timeout haun $hakuOid sijoittelun tallennuksen jälkeisestä analyzestä!!!", te)
      case sqlt:SQLTimeoutException => logger.warn(s"Timeout haun $hakuOid sijoittelun tallennuksen jälkeisestä analyzestä!!!", sqlt)
      case t => throw t
    }
  }

  private def storeValintatapajononHakemus(hakemus: SijoitteluHakemus,
                                           valintatulosOption: Option[Valintatulos],
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           jonosijaStatement: PreparedStatement,
                                           pistetietoStatement: PreparedStatement,
                                           insertValinnantulosStatement: PreparedStatement,
                                           updateValinnantulosStatement: PreparedStatement,
                                           valinnantilaStatement: PreparedStatement,
                                           tilankuvausStatement: PreparedStatement,
                                           tilaKuvausMappingStatement: PreparedStatement) = {
    val hakemusWrapper = SijoitteluajonHakemusWrapper(hakemus)
    createJonosijaInsertRow(sijoitteluajoId, hakukohdeOid, valintatapajonoOid, hakemusWrapper, jonosijaStatement)
    hakemus.getPistetiedot.asScala.foreach(createPistetietoInsertRow(sijoitteluajoId, valintatapajonoOid, HakemusOid(hakemus.getHakemusOid), _, pistetietoStatement))
    createValinnantilanKuvausInsertRow(hakemusWrapper, tilankuvausStatement)
    createValinnantilaInsertRow(hakukohdeOid, valintatapajonoOid, sijoitteluajoId, hakemusWrapper, valinnantilaStatement)
    valintatulosOption match {
      case None => createValinnantulosInsertRow(hakemusWrapper, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, insertValinnantulosStatement)
      case Some(valintatulos) => createValinnantulosUpdateRow(hakemusWrapper, valintatulos, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, updateValinnantulosStatement)
    }
    createTilaKuvausMappingInsertRow(hakemusWrapper, hakukohdeOid, valintatapajonoOid, tilaKuvausMappingStatement)
  }

  private def createStatement(sql:String) = (connection:java.sql.Connection) => connection.prepareStatement(sql)

  private def createJonosijaStatement = createStatement("""insert into jonosijat (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid, prioriteetti,
          jonosija, varasijan_numero, onko_muuttunut_viime_sijoittelussa, pisteet, tasasijajonosija, hyvaksytty_harkinnanvaraisesti,
          siirtynyt_toisesta_valintatapajonosta, tila) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::valinnantila)""")

  private def createJonosijaInsertRow(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemus: SijoitteluajonHakemusWrapper, statement: PreparedStatement) = {
    val SijoitteluajonHakemusWrapper(hakemusOid, _, prioriteetti, jonosija, varasijanNumero,
    onkoMuuttunutViimeSijoittelussa, pisteet, tasasijaJonosija, hyvaksyttyHarkinnanvaraisesti, siirtynytToisestaValintatapajonosta,
    valinnantila, tilanKuvaukset, tilankuvauksenTarkenne, tarkenteenLisatieto, hyvaksyttyHakijaryhmista, _) = hakemus

    statement.setString(1, valintatapajonoOid.toString)
    statement.setLong(2, sijoitteluajoId)
    statement.setString(3, hakukohdeOid.toString)
    statement.setString(4, hakemusOid.toString)
    statement.setInt(5, prioriteetti)
    statement.setInt(6, jonosija)
    varasijanNumero match {
      case Some(x) => statement.setInt(7, x)
      case _ => statement.setNull(7, Types.INTEGER)
    }
    statement.setBoolean(8, onkoMuuttunutViimeSijoittelussa)
    statement.setBigDecimal(9, pisteet.map(_.bigDecimal).orNull)
    statement.setInt(10, tasasijaJonosija)
    statement.setBoolean(11, hyvaksyttyHarkinnanvaraisesti)
    statement.setBoolean(12, siirtynytToisestaValintatapajonosta)
    statement.setString(13, valinnantila.toString)
    statement.addBatch()
  }

  private def createPistetietoStatement = createStatement("""insert into pistetiedot (sijoitteluajo_id, hakemus_oid, valintatapajono_oid,
    tunniste, arvo, laskennallinen_arvo, osallistuminen) values (?, ?, ?, ?, ?, ?, ?)""")

  private def createPistetietoInsertRow(sijoitteluajoId: Long, valintatapajonoOid: ValintatapajonoOid, hakemusOid: HakemusOid, pistetieto: Pistetieto, statement: PreparedStatement) = {
    val SijoitteluajonPistetietoWrapper(tunniste, arvo, laskennallinenArvo, osallistuminen)
    = SijoitteluajonPistetietoWrapper(pistetieto)

    statement.setLong(1, sijoitteluajoId)
    statement.setString(2, hakemusOid.toString)
    statement.setString(3, valintatapajonoOid.toString)
    statement.setString(4, tunniste)
    statement.setString(5, arvo.orNull)
    statement.setString(6, laskennallinenArvo.orNull)
    statement.setString(7, osallistuminen.orNull)
    statement.addBatch()
  }

  private def createInsertValinnantulosStatement = createStatement(
    """insert into valinnantulokset(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ilmoittaja,
             selite
           ) values (?, ?, ?, ?::text, 'Sijoittelun tallennus')
           on conflict on constraint valinnantulokset_pkey do nothing""")

  private def createValinnantulosInsertRow(hakemus:SijoitteluajonHakemusWrapper,
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           valinnantulosStatement:PreparedStatement) = {
    valinnantulosStatement.setString(1, valintatapajonoOid.toString)
    valinnantulosStatement.setString(2, hakemus.hakemusOid.toString)
    valinnantulosStatement.setString(3, hakukohdeOid.toString)
    valinnantulosStatement.setLong(4, sijoitteluajoId)
    valinnantulosStatement.addBatch()
  }


  private def createUpdateValinnantulosStatement = createStatement(
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

  private def createValinnantulosUpdateRow(hakemus:SijoitteluajonHakemusWrapper,
                                           valintatulos: Valintatulos,
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           valinnantulosStatement:PreparedStatement) = {

    val read = valintatulos.getRead.getTime

    valinnantulosStatement.setString(1, valintatapajonoOid.toString)
    valinnantulosStatement.setString(2, hakemus.hakemusOid.toString)
    valinnantulosStatement.setString(3, hakukohdeOid.toString)
    valinnantulosStatement.setBoolean(4, valintatulos.getJulkaistavissa)
    valinnantulosStatement.setBoolean(5, valintatulos.getHyvaksyttyVarasijalta)
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

  private def createValinnantilaInsertRow(hakukohdeOid: HakukohdeOid,
                                          valintatapajonoOid: ValintatapajonoOid,
                                          sijoitteluajoId: Long,
                                          hakemus: SijoitteluajonHakemusWrapper,
                                          statement: PreparedStatement) = {
    val tilanViimeisinMuutos = hakemus.tilaHistoria
      .filter(_.tila.equals(hakemus.tila))
      .map(_.luotu)
      .sortWith(_.after(_))
      .headOption.getOrElse(new Date())

    statement.setString(1, hakukohdeOid.toString)
    statement.setString(2, valintatapajonoOid.toString)
    statement.setString(3, hakemus.hakemusOid.toString)
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
                                               hakukohdeOid: HakukohdeOid,
                                               valintatapajonoOid: ValintatapajonoOid,
                                               statement: PreparedStatement) = {
    statement.setInt(1, hakemusWrapper.tilankuvauksenHash)
    statement.setString(2, hakemusWrapper.tarkenteenLisatieto.orNull)
    statement.setString(3, hakukohdeOid.toString)
    statement.setString(4, valintatapajonoOid.toString)
    statement.setString(5, hakemusWrapper.hakemusOid.toString)
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

  private def insertHakukohde(hakuOid: HakuOid, hakukohde: Hakukohde) = {
    val SijoitteluajonHakukohdeWrapper(sijoitteluajoId, oid, kaikkiJonotSijoiteltu) = SijoitteluajonHakukohdeWrapper(hakukohde)
    sqlu"""insert into sijoitteluajon_hakukohteet (sijoitteluajo_id, haku_oid, hakukohde_oid, kaikki_jonot_sijoiteltu)
             values (${sijoitteluajoId}, ${hakuOid}, ${oid}, ${kaikkiJonotSijoiteltu})"""
  }

  private def insertValintatapajono(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid, valintatapajono: Valintatapajono) = {
    val SijoitteluajonValintatapajonoWrapper(oid, nimi, prioriteetti, tasasijasaanto, aloituspaikat, alkuperaisetAloituspaikat,
    eiVarasijatayttoa, kaikkiEhdonTayttavatHyvaksytaan, poissaOlevaTaytto, varasijat, varasijaTayttoPaivat,
    varasijojaKaytetaanAlkaen, varasijojaTaytetaanAsti, tayttojono, alinHyvaksyttyPistemaara, _)
    = SijoitteluajonValintatapajonoWrapper(valintatapajono)

    val varasijojaKaytetaanAlkaenTs:Option[Timestamp] = varasijojaKaytetaanAlkaen.flatMap(d => Option(new Timestamp(d.getTime)))
    val varasijojaTaytetaanAstiTs:Option[Timestamp] = varasijojaTaytetaanAsti.flatMap(d => Option(new Timestamp(d.getTime)))

    val ensureValintaesitys =
      sqlu"""insert into valintaesitykset (
                 hakukohde_oid,
                 valintatapajono_oid,
                 hyvaksytty
             ) values (
                 $hakukohdeOid,
                 $oid,
                 null::timestamp with time zone
             ) on conflict on constraint valintaesitykset_pkey do nothing
        """
    val insert =
      sqlu"""insert into valintatapajonot (
                 oid,
                 sijoitteluajo_id,
                 hakukohde_oid,
                 nimi,
                 prioriteetti,
                 tasasijasaanto,
                 aloituspaikat,
                 alkuperaiset_aloituspaikat,
                 kaikki_ehdon_tayttavat_hyvaksytaan,
                 poissaoleva_taytto,
                 ei_varasijatayttoa,
                 varasijat,
                 varasijatayttopaivat,
                 varasijoja_kaytetaan_alkaen,
                 varasijoja_taytetaan_asti,
                 tayttojono,
                 alin_hyvaksytty_pistemaara
             ) values (
                 ${oid},
                 ${sijoitteluajoId},
                 ${hakukohdeOid},
                 ${nimi},
                 ${prioriteetti},
                 ${tasasijasaanto.toString}::tasasijasaanto,
                 ${aloituspaikat},
                 ${alkuperaisetAloituspaikat},
                 ${kaikkiEhdonTayttavatHyvaksytaan},
                 ${poissaOlevaTaytto},
                 ${eiVarasijatayttoa},
                 ${varasijat},
                 ${varasijaTayttoPaivat},
                 ${varasijojaKaytetaanAlkaenTs},
                 ${varasijojaTaytetaanAstiTs},
                 ${tayttojono},
                 ${alinHyvaksyttyPistemaara}
             )"""
    ensureValintaesitys.andThen(insert)
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
                                        hakemusOid: HakemusOid,
                                        hyvaksyttyHakijaryhmasta:Boolean,
                                        statement: PreparedStatement) = {
    var i = 1
    statement.setString(i, hakijaryhmaOid); i += 1
    statement.setLong(i, sijoitteluajoId); i += 1
    statement.setString(i, hakemusOid.toString); i += 1
    statement.setBoolean(i, hyvaksyttyHakijaryhmasta); i += 1
    statement.addBatch()
  }

  private def createHyvaksyttyJaJulkaistuInsertStatement = createStatement(
    """with hakukohteen_hyvaksytyt_ja_julkaistut as (
         select distinct ti.henkilo_oid
         from valinnantilat ti
         inner join valinnantulokset tu on ti.hakukohde_oid = tu.hakukohde_oid and
           ti.valintatapajono_oid = tu.valintatapajono_oid and ti.hakemus_oid = tu.hakemus_oid
         where ti.hakukohde_oid = ? and tu.julkaistavissa = true and
           ti.tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila)
       ) insert into hyvaksytyt_ja_julkaistut_hakutoiveet(
            henkilo,
            hakukohde,
            hyvaksytty_ja_julkaistu,
            ilmoittaja,
            selite
        ) select henkilo_oid, ?, now(), ?::text, 'Sijoittelun tallennus'
          from hakukohteen_hyvaksytyt_ja_julkaistut
          on conflict on constraint hyvaksytyt_ja_julkaistut_hakutoiveet_pkey do nothing"""
  )

  private def createHyvaksyttyJaJulkaistuInsertRow(hakukohdeOid: HakukohdeOid,
                                                   sijoitteluajoId: Long,
                                                   statement: PreparedStatement) = {
    statement.setString(1, hakukohdeOid.toString)
    statement.setString(2, hakukohdeOid.toString)
    statement.setLong(3, sijoitteluajoId)
    statement.addBatch()
  }

  private def createHyvaksyttyJaJulkaistuDeleteStatement = createStatement(
    """with hakukohteen_hyvaksytyt as (
         select distinct henkilo_oid
         from valinnantilat
         where hakukohde_oid = ? and
         tila in ('Hyvaksytty'::valinnantila, 'VarasijaltaHyvaksytty'::valinnantila)
       ) delete from hyvaksytyt_ja_julkaistut_hakutoiveet
         where hakukohde = ? and henkilo not in (select * from hakukohteen_hyvaksytyt)""")

  private def createHyvaksyttyJaJulkaistuDeleteRow(hakukohdeOid: HakukohdeOid,
                                                   statement: PreparedStatement) = {
    statement.setString(1, hakukohdeOid.toString)
    statement.setString(2, hakukohdeOid.toString)
    statement.addBatch()
  }
}
