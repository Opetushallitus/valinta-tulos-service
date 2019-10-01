package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.{PreparedStatement, SQLTimeoutException, Timestamp, Types}
import java.util.Date
import java.util.concurrent.TimeUnit

import fi.vm.sade.sijoittelu.domain.{Hakukohde, SijoitteluAjo, Valintatapajono, Hakemus => SijoitteluHakemus, _}
import fi.vm.sade.utils.Timer.timed
import fi.vm.sade.valintatulosservice.valintarekisteri.db.StoreSijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile.api._

import scala.collection.JavaConverters._
import scala.concurrent.TimeoutException
import scala.concurrent.duration.Duration
import scala.util.Try

trait StoreSijoitteluRepositoryImpl extends StoreSijoitteluRepository with ValintarekisteriRepository {

  private val ANALYZE_AFTER_INSERT_THRESHOLD = 1000 // hakukohdetta haussa

  override def storeSijoittelu(sijoittelu: SijoitteluWrapper): Unit = timed(s"Haun ${sijoittelu.sijoitteluajo.getHakuOid} koko sijoittelun ${sijoittelu.sijoitteluajo.getSijoitteluajoId} tallennus", 100) {
    val sijoitteluajoId = sijoittelu.sijoitteluajo.getSijoitteluajoId
    val hakuOid = HakuOid(sijoittelu.sijoitteluajo.getHakuOid)
    runBlocking(insertSijoitteluajo(sijoittelu.sijoitteluajo)
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.map(insertHakukohde(hakuOid, _))))
      .andThen(DBIO.sequence(
        sijoittelu.hakukohteet.flatMap(hakukohde =>
          hakukohde.getValintatapajonot.asScala.map(insertValintatapajono(sijoitteluajoId, HakukohdeOid(hakukohde.getOid), _)))))
      .andThen(setFalseHyvaksyttyVarasijaltaAndHyvaksyPeruuntunut(sijoitteluajoId))
      .andThen(SimpleDBIO { session =>
        var jonosijaStatement:Option[PreparedStatement] = None
        var insertValinnantulosStatement:Option[PreparedStatement] = None
        var updateValinnantulosStatement:Option[PreparedStatement] = None
        var valinnantilaStatement:Option[PreparedStatement] = None
        var tilankuvausStatement:Option[PreparedStatement] = None
        var tilaKuvausMappingStatement:Option[PreparedStatement] = None
        var deleteHyvaksyttyJulkaistuStatement:Option[PreparedStatement] = None
        var insertHyvaksyttyJulkaistuStatement:Option[PreparedStatement] = None
        try {
          jonosijaStatement = Some(createJonosijaStatement(session.connection))
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
          timed(s"Haun $hakuOid valinnantilojen tallennus", 100) { valinnantilaStatement.get.executeBatch }
          timed(s"Haun $hakuOid uusien valinnantulosten tallennus", 100) { insertValinnantulosStatement.get.executeBatch }
          timed(s"Haun $hakuOid päivittyneiden valinnantulosten tallennus", 100) { updateValinnantulosStatement.get.executeBatch }
          timed(s"Haun $hakuOid tilankuvaus-mäppäysten tallennus", 100) { tilaKuvausMappingStatement.get.executeBatch }
          timed(s"Haun $hakuOid hyväksytty ja julkaistu -päivämäärien poisto", 100) { deleteHyvaksyttyJulkaistuStatement.get.executeBatch }
          timed(s"Haun $hakuOid hyväksytty ja julkaistu -päivämäärien tallennus", 100) { insertHyvaksyttyJulkaistuStatement.get.executeBatch }
        } finally {
          Try(tilankuvausStatement.foreach(_.close))
          Try(jonosijaStatement.foreach(_.close))
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

    if(sijoittelu.hakukohteet.length > ANALYZE_AFTER_INSERT_THRESHOLD) {
      try {
        timed(s"Haun $hakuOid sijoittelun tallennuksen jälkeinen analyze", 100) {
          runBlocking(DBIO.seq(sqlu"""analyze jonosijat"""),
            Duration(20, TimeUnit.MINUTES))
        }
      } catch {
        case te: TimeoutException => logger.warn(s"Timeout haun $hakuOid sijoittelun tallennuksen jälkeisestä analyzestä!!!", te)
        case sqlt: SQLTimeoutException => logger.warn(s"Timeout haun $hakuOid sijoittelun tallennuksen jälkeisestä analyzestä!!!", sqlt)
      }
    }
  }

  private def setFalseHyvaksyttyVarasijaltaAndHyvaksyPeruuntunut(sijoitteluajoId: Long): DBIO[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    sql"""update valinnantulokset
          set hyvaksytty_varasijalta = false,
              ilmoittaja = ${sijoitteluajoId},
              selite = 'Sijoittelun tallennus'
          where valintatapajono_oid in (select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}) and
                hyvaksytty_varasijalta
          returning hakukohde_oid, valintatapajono_oid, hakemus_oid
        """.as[(HakukohdeOid, ValintatapajonoOid, HakemusOid)].map(t => {
      if (t.nonEmpty) {
        logger.info("Asetettiin Hyväksytty varasijalta pois: " + t.map(tt => s"hakukohde ${tt._1}, valintatapajono ${tt._2}, hakemus ${tt._3}").mkString("; "))
      }
      ()
    }).andThen(
      sql"""update valinnantulokset
            set hyvaksy_peruuntunut = false,
                ilmoittaja = ${sijoitteluajoId},
                selite = 'Sijoittelun tallennus'
            where valintatapajono_oid in (select oid from valintatapajonot where sijoitteluajo_id = ${sijoitteluajoId}) and
                  hyvaksy_peruuntunut
            returning hakukohde_oid, valintatapajono_oid, hakemus_oid
        """.as[(HakukohdeOid, ValintatapajonoOid, HakemusOid)].map(t => {
        if (t.nonEmpty) {
          logger.info("Asetettiin Hyväksy peruuntunut pois: " + t.map(tt => s"hakukohde ${tt._1}, valintatapajono ${tt._2}, hakemus ${tt._3}").mkString("; "))
        }
        ()
      })
    )
  }

  private def storeValintatapajononHakemus(hakemus: SijoitteluHakemus,
                                           valintatulosOption: Option[Valintatulos],
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           jonosijaStatement: PreparedStatement,
                                           insertValinnantulosStatement: PreparedStatement,
                                           updateValinnantulosStatement: PreparedStatement,
                                           valinnantilaStatement: PreparedStatement,
                                           tilankuvausStatement: PreparedStatement,
                                           tilaKuvausMappingStatement: PreparedStatement) = {
    createJonosijaInsertRow(sijoitteluajoId, hakukohdeOid, valintatapajonoOid, hakemus, jonosijaStatement)
    createValinnantilanKuvausInsertRow(hakemus, tilankuvausStatement)
    createValinnantilaInsertRow(hakukohdeOid, valintatapajonoOid, sijoitteluajoId, hakemus, valinnantilaStatement)
    valintatulosOption match {
      case None => createValinnantulosInsertRow(hakemus, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, insertValinnantulosStatement)
      case Some(valintatulos) => createValinnantulosUpdateRow(hakemus, valintatulos, sijoitteluajoId, hakukohdeOid, valintatapajonoOid, updateValinnantulosStatement)
    }
    createTilaKuvausMappingInsertRow(hakemus, hakukohdeOid, valintatapajonoOid, tilaKuvausMappingStatement)
  }

  private def createStatement(sql:String) = (connection:java.sql.Connection) => connection.prepareStatement(sql)

  private def createJonosijaStatement = createStatement("""insert into jonosijat (valintatapajono_oid, sijoitteluajo_id, hakukohde_oid, hakemus_oid, prioriteetti,
          jonosija, varasijan_numero, onko_muuttunut_viime_sijoittelussa, pisteet, tasasijajonosija, hyvaksytty_harkinnanvaraisesti,
          siirtynyt_toisesta_valintatapajonosta, tila) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::valinnantila)""")

  private def createJonosijaInsertRow(sijoitteluajoId: Long, hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid, hakemus: SijoitteluHakemus, statement: PreparedStatement) = {
    statement.setString(1, valintatapajonoOid.toString)
    statement.setLong(2, sijoitteluajoId)
    statement.setString(3, hakukohdeOid.toString)
    statement.setString(4, hakemus.getHakemusOid)
    statement.setInt(5, hakemus.getPrioriteetti)
    statement.setInt(6, hakemus.getJonosija)
    if (hakemus.getVarasijanNumero == null) {
      statement.setNull(7, Types.INTEGER)
    } else {
      statement.setInt(7, hakemus.getVarasijanNumero)
    }
    statement.setBoolean(8, if (hakemus.isOnkoMuuttunutViimeSijoittelussa == null) false else hakemus.isOnkoMuuttunutViimeSijoittelussa)
    statement.setBigDecimal(9, hakemus.getPisteet)
    statement.setInt(10, hakemus.getTasasijaJonosija)
    statement.setBoolean(11, hakemus.isHyvaksyttyHarkinnanvaraisesti)
    statement.setBoolean(12, hakemus.getSiirtynytToisestaValintatapajonosta)
    statement.setString(13, Valinnantila(hakemus.getTila).toString)
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

  private def createValinnantulosInsertRow(hakemus:SijoitteluHakemus,
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           valinnantulosStatement:PreparedStatement) = {
    valinnantulosStatement.setString(1, valintatapajonoOid.toString)
    valinnantulosStatement.setString(2, hakemus.getHakemusOid)
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
             ilmoittaja,
             selite
           ) values (?, ?, ?, ?, ?::text, 'Sijoittelun tallennus')
           on conflict on constraint valinnantulokset_pkey do update set
             julkaistavissa = excluded.julkaistavissa,
             ilmoittaja = excluded.ilmoittaja,
             selite = excluded.selite
           where valinnantulokset.julkaistavissa <> excluded.julkaistavissa
             and valinnantulokset.system_time @> ?::timestamp with time zone
             and ?::valinnantila <> 'Peruuntunut'::valinnantila""")

  private def createValinnantulosUpdateRow(hakemus:SijoitteluHakemus,
                                           valintatulos: Valintatulos,
                                           sijoitteluajoId:Long,
                                           hakukohdeOid: HakukohdeOid,
                                           valintatapajonoOid: ValintatapajonoOid,
                                           valinnantulosStatement:PreparedStatement) = {

    val read = valintatulos.getRead.getTime

    valinnantulosStatement.setString(1, valintatapajonoOid.toString)
    valinnantulosStatement.setString(2, hakemus.getHakemusOid)
    valinnantulosStatement.setString(3, hakukohdeOid.toString)
    valinnantulosStatement.setBoolean(4, valintatulos.getJulkaistavissa)
    valinnantulosStatement.setLong(5, sijoitteluajoId)
    valinnantulosStatement.setTimestamp(6, new java.sql.Timestamp(read))
    valinnantulosStatement.setString(7, Valinnantila(hakemus.getTila).toString)
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
                                          hakemus: SijoitteluHakemus,
                                          statement: PreparedStatement) = {
    val tilanViimeisinMuutos = hakemus.getTilaHistoria.asScala
      .filter(_.getTila.equals(hakemus.getTila))
      .map(_.getLuotu)
      .sortWith(_.after(_))
      .headOption.getOrElse(new Date())

    statement.setString(1, hakukohdeOid.toString)
    statement.setString(2, valintatapajonoOid.toString)
    statement.setString(3, hakemus.getHakemusOid)
    statement.setString(4, Valinnantila(hakemus.getTila).toString)
    statement.setTimestamp(5, new Timestamp(tilanViimeisinMuutos.getTime))
    statement.setLong(6, sijoitteluajoId)
    statement.setString(7, hakemus.getHakijaOid)

    statement.addBatch()
  }

  private def createTilaKuvausMappingStatement = createStatement(
    """insert into tilat_kuvaukset (
          tilankuvaus_hash,
          hakukohde_oid,
          valintatapajono_oid,
          hakemus_oid) values (?, ?, ?, ?)
       on conflict on constraint tilat_kuvaukset_pkey do update set
           tilankuvaus_hash = excluded.tilankuvaus_hash
       where tilat_kuvaukset.tilankuvaus_hash <> excluded.tilankuvaus_hash
    """)

  private def createTilaKuvausMappingInsertRow(hakemus: SijoitteluHakemus,
                                               hakukohdeOid: HakukohdeOid,
                                               valintatapajonoOid: ValintatapajonoOid,
                                               statement: PreparedStatement) = {
    statement.setInt(1, hakemus.getTilanKuvaukset.hashCode())
    statement.setString(2, hakukohdeOid.toString)
    statement.setString(3, valintatapajonoOid.toString)
    statement.setString(4, hakemus.getHakemusOid)
    statement.addBatch()
  }

  private def createTilankuvausStatement = createStatement("""insert into valinnantilan_kuvaukset (hash, tilan_tarkenne, text_fi, text_sv, text_en)
      values (?, ?::valinnantilanTarkenne, ?, ?, ?) on conflict do nothing""")

  private def createValinnantilanKuvausInsertRow(h: SijoitteluHakemus, s: PreparedStatement) = {
    s.setInt(1, h.getTilanKuvaukset.hashCode())
    s.setString(2, ValinnantilanTarkenne.getValinnantilanTarkenne(h.getTilankuvauksenTarkenne).toString)
    s.setString(3, h.getTilanKuvaukset.get("FI"))
    s.setString(4, h.getTilanKuvaukset.get("SV"))
    s.setString(5, h.getTilanKuvaukset.get("EN"))
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
    varasijojaKaytetaanAlkaen, varasijojaTaytetaanAsti, tayttojono, alinHyvaksyttyPistemaara, _,
    sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa, sivssnovSijoittelunViimeistenVarallaolijoidenJonosija)
    = SijoitteluajonValintatapajonoWrapper(valintatapajono)

    val varasijojaKaytetaanAlkaenTs:Option[Timestamp] = varasijojaKaytetaanAlkaen.flatMap(d => Option(new Timestamp(d.getTime)))
    val varasijojaTaytetaanAstiTs:Option[Timestamp] = varasijojaTaytetaanAsti.flatMap(d => Option(new Timestamp(d.getTime)))

    val ensureValintaesitys: DBIOAction[Int, NoStream, Effect] =
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
    val insert: DBIOAction[Int, NoStream, Effect] =
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
                 alin_hyvaksytty_pistemaara,
                 sijoiteltu_ilman_varasijasaantoja_niiden_ollessa_voimassa
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
                 ${alinHyvaksyttyPistemaara},
                 ${sijoiteltuIlmanVarasijasaantojaNiidenOllessaVoimassa}
             )"""

    val insertSivssnovJonosija: DBIOAction[AnyVal, NoStream, Effect] = sivssnovSijoittelunViimeistenVarallaolijoidenJonosija.map { jonosijaTieto: Valintatapajono.JonosijaTieto =>
      val hakemusOidsJson: String = compact(render(jonosijaTieto.hakemusOidit.asScala))
      sqlu"""insert into sivssnov_sijoittelun_varasijatayton_rajoitus (
             valintatapajono_oid,
             sijoitteluajo_id,
             hakukohde_oid,
             jonosija,
             tasasijajonosija,
             tila,
             hakemusoidit
           ) values (
             ${oid},
             ${sijoitteluajoId},
             ${hakukohdeOid},
             ${jonosijaTieto.jonosija},
             ${jonosijaTieto.tasasijaJonosija},
             ${Valinnantila(jonosijaTieto.tila).toString}::valinnantila,
             to_json(${hakemusOidsJson}::json)
         )"""
    }.getOrElse(DBIOAction.successful())
    ensureValintaesitys.andThen(insert).andThen(insertSivssnovJonosija)
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
