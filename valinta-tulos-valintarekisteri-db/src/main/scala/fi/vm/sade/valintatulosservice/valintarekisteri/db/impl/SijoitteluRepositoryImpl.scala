package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{PreparedStatement, Timestamp, Types}
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.driver.PostgresDriver.api._
import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatapajono, Hakemus => SijoitteluHakemus, _}

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait SijoitteluRepositoryImpl extends SijoitteluRepository with ValintarekisteriRepository {

  import scala.collection.JavaConverters._

  override def storeSijoittelu(sijoittelu: SijoitteluWrapper) = {
    val sijoitteluajoId = sijoittelu.sijoitteluajo.getSijoitteluajoId
    val hakuOid = sijoittelu.sijoitteluajo.getHakuOid
    runBlocking(insertSijoitteluajo(sijoittelu.sijoitteluajo)
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.map(insertHakukohde(hakuOid, _))))
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(hakukohde =>
          hakukohde.getValintatapajonot.asScala.map(insertValintatapajono(sijoitteluajoId, hakukohde.getOid, _)))))
      .andThen(SimpleDBIO { session =>
        val jonosijaStatement = createJonosijaStatement(session.connection)
        val pistetietoStatement = createPistetietoStatement(session.connection)
        val valinnantulosStatement = createValinnantulosStatement(session.connection)
        val valinnantilaStatement = createValinnantilaStatement(session.connection)
        val tilankuvausStatement = createTilankuvausStatement(session.connection)
        val tilaKuvausMappingStatement = createTilaKuvausMappingStatement(session.connection)
        sijoittelu.hakukohteet.foreach(hakukohde => {
          hakukohde.getValintatapajonot.asScala.foreach(valintatapajono => {
            valintatapajono.getHakemukset.asScala.foreach(hakemus => {
              storeValintatapajononHakemus(
                hakemus,
                sijoitteluajoId,
                hakukohde.getOid,
                valintatapajono.getOid,
                jonosijaStatement,
                pistetietoStatement,
                valinnantulosStatement,
                valinnantilaStatement,
                tilankuvausStatement,
                tilaKuvausMappingStatement
              )
            })
          })
        })
        time(s"Haun $hakuOid tilankuvauksien tallennus") { tilankuvausStatement.executeBatch() }
        time(s"Haun $hakuOid jonosijojen tallennus") { jonosijaStatement.executeBatch }
        time(s"Haun $hakuOid pistetietojen tallennus") { pistetietoStatement.executeBatch }
        time(s"Haun $hakuOid valinnantilojen tallennus") { valinnantilaStatement.executeBatch }
        time(s"Haun $hakuOid valinnantulosten tallennus") { valinnantulosStatement.executeBatch }
        time(s"Haun $hakuOid tilankuvaus-mäppäysten tallennus") { tilaKuvausMappingStatement.executeBatch }
        tilankuvausStatement.close()
        jonosijaStatement.close()
        pistetietoStatement.close()
        valinnantilaStatement.close()
        valinnantulosStatement.close()
        tilaKuvausMappingStatement.close()
      })
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(_.getHakijaryhmat.asScala).map(insertHakijaryhma(sijoitteluajoId, _))))
      .andThen(SimpleDBIO { session =>
        val statement = prepareInsertHakijaryhmanHakemus(session.connection)
        sijoittelu.hakukohteet.foreach(hakukohde => {
          hakukohde.getHakijaryhmat.asScala.foreach(hakijaryhma => {
            val hyvaksytyt = hakukohde.getValintatapajonot.asScala
              .flatMap(_.getHakemukset.asScala)
              .filter(_.getHyvaksyttyHakijaryhmista.contains(hakijaryhma.getOid))
              .map(_.getHakemusOid)
              .toSet
            hakijaryhma.getHakemusOid.asScala.foreach(hakemusOid => {
              insertHakijaryhmanHakemus(hakijaryhma.getOid, sijoitteluajoId, hakemusOid, hyvaksytyt.contains(hakemusOid), statement)
            })
          })
        })
        time(s"Haun $hakuOid hakijaryhmien hakemusten tallennus") { statement.executeBatch() }
        statement.close()
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
    createValinnantulosInsertRow(hakemusWrapper, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, valinnantulosStatement)
    createTilaKuvausMappingInsertRow(hakemusWrapper, hakukohdeOid, valintatapajonoOid, tilaKuvausMappingStatement)
  }

  private def createStatement(sql:String) = (connection:java.sql.Connection) => connection.prepareStatement(sql)

  private def createJonosijaStatement = createStatement("""insert into jonosijat (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid, hakija_oid, etunimi, sukunimi, prioriteetti,
          jonosija, varasijan_numero, onko_muuttunut_viime_sijoittelussa, pisteet, tasasijajonosija, hyvaksytty_harkinnanvaraisesti,
          siirtynyt_toisesta_valintatapajonosta, tila) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::valinnantila)""")

  private def createJonosijaInsertRow(sijoitteluajoId: Long, hakukohdeOid: String, valintatapajonoOid: String, hakemus: SijoitteluajonHakemusWrapper, statement: PreparedStatement) = {
    val SijoitteluajonHakemusWrapper(hakemusOid, hakijaOid, etunimi, sukunimi, prioriteetti, jonosija, varasijanNumero,
    onkoMuuttunutViimeSijoittelussa, pisteet, tasasijaJonosija, hyvaksyttyHarkinnanvaraisesti, siirtynytToisestaValintatapajonosta,
    valinnantila, tilanKuvaukset, tilankuvauksenTarkenne, tarkenteenLisatieto, hyvaksyttyHakijaryhmista, _) = hakemus

    statement.setString(1, valintatapajonoOid)
    statement.setLong(2, sijoitteluajoId)
    statement.setString(3, hakukohdeOid)
    statement.setString(4, hakemusOid)
    statement.setString(5, hakijaOid.orNull)
    statement.setString(6, etunimi.orNull)
    statement.setString(7, sukunimi.orNull)
    statement.setInt(8, prioriteetti)
    statement.setInt(9, jonosija)
    varasijanNumero match {
      case Some(x) => statement.setInt(10, x)
      case _ => statement.setNull(10, Types.INTEGER)
    }
    statement.setBoolean(11, onkoMuuttunutViimeSijoittelussa)
    statement.setBigDecimal(12, pisteet.map(_.bigDecimal).orNull)
    statement.setInt(13, tasasijaJonosija)
    statement.setBoolean(14, hyvaksyttyHarkinnanvaraisesti)
    statement.setBoolean(15, siirtynytToisestaValintatapajonosta)
    statement.setString(16, valinnantila.toString)
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
    statement.addBatch
  }

  private def createValinnantulosStatement = createStatement(
    """insert into valinnantulokset (
           valintatapajono_oid,
           hakemus_oid,
           hakukohde_oid,
           ilmoittaja,
           selite
       ) values (?, ?, ?, ?::text, 'Sijoittelun tallennus')
       on conflict on constraint valinnantulokset_pkey do nothing""")

  private def createValinnantulosInsertRow(hakemus:SijoitteluajonHakemusWrapper,
                                           sijoitteluajoId:Long,
                                           hakukohdeOid:String,
                                           valintatapajonoOid:String,
                                           valinnantulosStatement:PreparedStatement) = {
    valinnantulosStatement.setString(1, valintatapajonoOid)
    valinnantulosStatement.setString(2, hakemus.hakemusOid)
    valinnantulosStatement.setString(3, hakukohdeOid)
    valinnantulosStatement.setLong(4, sijoitteluajoId)
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

  private def newJavaSqlDateOrNull(date:Option[Date]) = date match {
    case Some(x) => new java.sql.Timestamp(x.getTime)
    case _ => null
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
    varasijojaKaytetaanAlkaen, varasijojaTaytetaanAsti, tayttojono, hyvaksytty, varalla, alinHyvaksyttyPistemaara, valintaesitysHyvaksytty)
    = SijoitteluajonValintatapajonoWrapper(valintatapajono)

    val varasijojaKaytetaanAlkaenTs:Option[Timestamp] = varasijojaKaytetaanAlkaen.flatMap(d => Option(new Timestamp(d.getTime)))
    val varasijojaTaytetaanAstiTs:Option[Timestamp] = varasijojaTaytetaanAsti.flatMap(d => Option(new Timestamp(d.getTime)))

    sqlu"""insert into valintatapajonot (oid, sijoitteluajo_id, hakukohde_oid, nimi, prioriteetti, tasasijasaanto, aloituspaikat,
           alkuperaiset_aloituspaikat, kaikki_ehdon_tayttavat_hyvaksytaan, poissaoleva_taytto, ei_varasijatayttoa,
           varasijat, varasijatayttopaivat, varasijoja_kaytetaan_alkaen, varasijoja_taytetaan_asti, tayttojono, hyvaksytty, varalla,
           alin_hyvaksytty_pistemaara, valintaesitys_hyvaksytty)
           values (${oid}, ${sijoitteluajoId}, ${hakukohdeOid}, ${nimi}, ${prioriteetti}, ${tasasijasaanto.toString}::tasasijasaanto, ${aloituspaikat},
           ${alkuperaisetAloituspaikat}, ${kaikkiEhdonTayttavatHyvaksytaan},
           ${poissaOlevaTaytto}, ${eiVarasijatayttoa}, ${varasijat}, ${varasijaTayttoPaivat},
           ${varasijojaKaytetaanAlkaenTs}, ${varasijojaTaytetaanAstiTs}, ${tayttojono},
           ${hyvaksytty}, ${varalla}, ${alinHyvaksyttyPistemaara}, ${valintaesitysHyvaksytty})"""
  }

  private def dateToTimestamp(date:Option[Date]): Timestamp = date match {
    case Some(d) => new java.sql.Timestamp(d.getTime)
    case None => null
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

  override def getLatestSijoitteluajoId(hakuOid:String): Option[Long] = {
    runBlocking(
      sql"""select id
            from sijoitteluajot
            where haku_oid = ${hakuOid}
            order by id desc
            limit 1""".as[Long]).headOption
  }

  override def getSijoitteluajo(sijoitteluajoId: Long): Option[SijoitteluajoRecord] = {
    runBlocking(
      sql"""select id, haku_oid, start, sijoitteluajot.end
            from sijoitteluajot
            where id = ${sijoitteluajoId}""".as[SijoitteluajoRecord]).headOption
  }

  override def getSijoitteluajonHakukohteet(sijoitteluajoId: Long): List[SijoittelunHakukohdeRecord] = {
    runBlocking(
      sql"""select sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu
            from sijoitteluajon_hakukohteet sh
            where sh.sijoitteluajo_id = ${sijoitteluajoId}
            group by sh.sijoitteluajo_id, sh.hakukohde_oid, sh.kaikki_jonot_sijoiteltu""".as[SijoittelunHakukohdeRecord]).toList
  }

  override def getSijoitteluajonValintatapajonot(sijoitteluajoId: Long): List[ValintatapajonoRecord] = {
    runBlocking(
      sql"""select tasasijasaanto, oid, nimi, prioriteetti, aloituspaikat, alkuperaiset_aloituspaikat,
            alin_hyvaksytty_pistemaara, ei_varasijatayttoa, kaikki_ehdon_tayttavat_hyvaksytaan, poissaoleva_taytto,
            valintaesitys_hyvaksytty, hyvaksytty, varalla, varasijat,
            varasijatayttopaivat, varasijoja_kaytetaan_alkaen, varasijoja_taytetaan_asti, tayttojono, hakukohde_oid
            from valintatapajonot
            where sijoitteluajo_id = ${sijoitteluajoId}""".as[ValintatapajonoRecord]).toList
  }

  override def getSijoitteluajonHakemukset(sijoitteluajoId:Long): List[HakemusRecord] = {
    runBlocking(
      sql"""select j.hakija_oid, j.hakemus_oid, j.pisteet, j.etunimi, j.sukunimi, j.prioriteetti, j.jonosija,
            j.tasasijajonosija, vt.tila, t_k.tilankuvaus_hash, t_k.tarkenteen_lisatieto, j.hyvaksytty_harkinnanvaraisesti, j.varasijan_numero,
            j.onko_muuttunut_viime_sijoittelussa,
            j.siirtynyt_toisesta_valintatapajonosta, j.valintatapajono_oid
            from jonosijat as j
            join valinnantulokset as v
            on v.valintatapajono_oid = j.valintatapajono_oid
              and v.hakemus_oid = j.hakemus_oid
              and v.hakukohde_oid = j.hakukohde_oid
            join valinnantilat as vt
            on vt.valintatapajono_oid = v.valintatapajono_oid
              and vt.hakemus_oid = v.hakemus_oid
              and vt.hakukohde_oid = v.hakukohde_oid
            join tilat_kuvaukset t_k
            on v.valintatapajono_oid = t_k.valintatapajono_oid
              and v.hakemus_oid = t_k.hakemus_oid
            where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusRecord], Duration(30, TimeUnit.SECONDS)).toList
  }

  override def getSijoitteluajonHakemuksetInChunks(sijoitteluajoId:Long, chunkSize:Int = 300): List[HakemusRecord] = {
    def readHakemukset(offset:Int = 0): List[HakemusRecord] = {
      runBlocking(sql"""
                     with vj as (
                       select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}
                       order by oid desc limit ${chunkSize} offset ${offset} )
                       select j.hakija_oid, j.hakemus_oid, j.pisteet, j.etunimi, j.sukunimi, j.prioriteetti, j.jonosija,
            j.tasasijajonosija, vt.tila, t_k.tilankuvaus_hash, t_k.tarkenteen_lisatieto, j.hyvaksytty_harkinnanvaraisesti, j.varasijan_numero,
            j.onko_muuttunut_viime_sijoittelussa,
            j.siirtynyt_toisesta_valintatapajonosta, j.valintatapajono_oid
            from jonosijat as j
            join valinnantulokset as v
            on v.valintatapajono_oid = j.valintatapajono_oid
              and v.hakemus_oid = j.hakemus_oid
              and v.hakukohde_oid = j.hakukohde_oid
            join valinnantilat as vt
            on vt.valintatapajono_oid = v.valintatapajono_oid
              and vt.hakemus_oid = v.hakemus_oid
              and vt.hakukohde_oid = v.hakukohde_oid
            join tilat_kuvaukset t_k
            on t_k.valintatapajono_oid = vt.valintatapajono_oid
              and t_k.hakemus_oid = vt.hakemus_oid
              and t_k.hakukohde_oid = vt.hakukohde_oid
            inner join vj on vj.oid = j.valintatapajono_oid
            where j.sijoitteluajo_id = ${sijoitteluajoId}""".as[HakemusRecord]).toList match {
        case result if result.size == 0 => result
        case result => result ++ readHakemukset(offset + chunkSize)
      }
    }
    readHakemukset()
  }

  def getSijoitteluajonHakemustenHakijaryhmat(sijoitteluajoId:Long): Map[String,Set[String]] = {
    runBlocking(
      sql"""select hh.hakemus_oid, hr.oid as hakijaryhma
            from hakijaryhmat hr
            inner join hakijaryhman_hakemukset hh on hr.oid = hh.hakijaryhma_oid and hr.sijoitteluajo_id = hh.sijoitteluajo_id
            where hr.sijoitteluajo_id = ${sijoitteluajoId};""".as[(String,String)]).groupBy(_._1).map { case (k,v) => (k,v.map(_._2).toSet) }
  }

  override def getSijoitteluajonTilahistoriat(sijoitteluajoId:Long): List[TilaHistoriaRecord] = {
    runBlocking(
      sql"""select lower(system_time) from sijoitteluajot where id = ${sijoitteluajoId}""".as[Timestamp].map(_.head).flatMap(ts =>
        sql"""select vt.valintatapajono_oid, vt.hakemus_oid, vt.tila, vt.tilan_viimeisin_muutos as luotu
              from valinnantilat as vt
              where exists (select 1 from jonosijat as j
                            where j.hakukohde_oid = vt.hakukohde_oid
                                and j.valintatapajono_oid = vt.valintatapajono_oid
                                and j.hakemus_oid = vt.hakemus_oid
                                and j.sijoitteluajo_id = ${sijoitteluajoId})
                  and vt.system_time @> ${ts}::timestamptz
              union all
              select th.valintatapajono_oid, th.hakemus_oid, th.tila, th.tilan_viimeisin_muutos as luotu
              from valinnantilat_history as th
              where exists (select 1 from jonosijat as j
                            where j.hakukohde_oid = th.hakukohde_oid
                                and j.valintatapajono_oid = th.valintatapajono_oid
                                and j.hakemus_oid = th.hakemus_oid
                                and j.sijoitteluajo_id = ${sijoitteluajoId})
                  and lower(th.system_time) <= ${ts}::timestamptz""".as[TilaHistoriaRecord])).toList
  }

  override def getValinnantilanKuvaukset(tilankuvausHashes:List[Int]): Map[Int,TilankuvausRecord] = tilankuvausHashes match {
    case x if 0 == tilankuvausHashes.size => Map()
    case _ => {
      val inParameter = tilankuvausHashes.map(id => s"'$id'").mkString(",")
      runBlocking(
        sql"""select hash, tilan_tarkenne, text_fi, text_sv, text_en
              from valinnantilan_kuvaukset
              where hash in (#${inParameter})""".as[TilankuvausRecord]).map(v => (v.hash, v)).toMap
    }
  }

  override def getSijoitteluajonHakijaryhmat(sijoitteluajoId: Long): List[HakijaryhmaRecord] = {
    runBlocking(
      sql"""select prioriteetti, oid, nimi, hakukohde_oid, kiintio, kayta_kaikki, sijoitteluajo_id,
            tarkka_kiintio, kaytetaan_ryhmaan_kuuluvia, valintatapajono_oid, hakijaryhmatyyppikoodi_uri
            from hakijaryhmat
            where sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaryhmaRecord]).toList
  }

  override def getSijoitteluajonHakijaryhmanHakemukset(hakijaryhmaOid: String, sijoitteluajoId:Long): List[String] = {
    runBlocking(
      sql"""select hakemus_oid
            from hakijaryhman_hakemukset
            where hakijaryhma_oid = ${hakijaryhmaOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[String]).toList
  }

  override def getHakemuksenHakija(hakemusOid: String, sijoitteluajoId: Long): Option[HakijaRecord] = {
    runBlocking(
      sql"""select etunimi, sukunimi, hakemus_oid, hakija_oid
            from jonosijat
            where hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId}""".as[HakijaRecord]).headOption
  }

  override def getHakemuksenHakutoiveet(hakemusOid: String, sijoitteluajoId: Long): List[HakutoiveRecord] = {
    runBlocking(
      sql"""with j as (select * from jonosijat where hakemus_oid = ${hakemusOid} and sijoitteluajo_id = ${sijoitteluajoId})
            select j.hakemus_oid, j.prioriteetti, v.hakukohde_oid, vt.tila, sh.kaikki_jonot_sijoiteltu
            from j
            left join valinnantulokset as v on v.hakemus_oid = j.hakemus_oid
                and v.valintatapajono_oid = j.valintatapajono_oid
                and v.hakukohde_oid = j.hakukohde_oid
            left join valinnantilat as vt on vt.hakemus_oid = v.hakemus_oid
                and vt.valintatapajono_oid = v.valintatapajono_oid
                and vt.hakukohde_oid = v.hakukohde_oid
            left join sijoitteluajon_hakukohteet as sh on sh.hakukohde_oid = v.hakukohde_oid
        """.as[HakutoiveRecord]).toList
  }

  override def getHakemuksenPistetiedot(hakemusOid:String, sijoitteluajoId:Long): List[PistetietoRecord] = {
    runBlocking(
      sql"""
         select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
         from pistetiedot
         where sijoitteluajo_id = ${sijoitteluajoId} and hakemus_oid = ${hakemusOid}""".as[PistetietoRecord]).toList
  }

  override def getSijoitteluajonPistetiedot(sijoitteluajoId:Long): List[PistetietoRecord] = {
    runBlocking(sql"""
       select valintatapajono_oid, hakemus_oid, tunniste, arvo, laskennallinen_arvo, osallistuminen
       from  pistetiedot
       where sijoitteluajo_id = ${sijoitteluajoId}""".as[PistetietoRecord],
      Duration(1, TimeUnit.MINUTES)
    ).toList
  }

  override def getSijoitteluajonPistetiedotInChunks(sijoitteluajoId:Long, chunkSize:Int = 200): List[PistetietoRecord] = {
    def readPistetiedot(offset:Int = 0): List[PistetietoRecord] = {
      runBlocking(sql"""
                     with v as (
                       select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}
                       order by oid desc limit ${chunkSize} offset ${offset} )
                       select p.valintatapajono_oid, p.hakemus_oid, p.tunniste, p.arvo, p.laskennallinen_arvo, p.osallistuminen
                                from  pistetiedot p
                                inner join v on p.valintatapajono_oid = v.oid
                                where p.sijoitteluajo_id = ${sijoitteluajoId}""".as[PistetietoRecord]).toList match {
        case result if result.size == 0 => result
        case result => result ++ readPistetiedot(offset + chunkSize)
      }
    }
    readPistetiedot()
  }
}
