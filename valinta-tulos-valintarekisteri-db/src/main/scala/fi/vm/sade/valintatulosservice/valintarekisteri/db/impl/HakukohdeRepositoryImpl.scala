package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakukohdeRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiKktutkintoonJohtavaHakukohde, EiYPSHakukohde, HakuOid, HakukohdeOid, HakukohdeRecord, YPSHakukohde}
import org.postgresql.util.PSQLException
import slick.jdbc.PostgresProfile.api._

trait HakukohdeRepositoryImpl extends HakukohdeRepository with ValintarekisteriRepository {

  override def findHakukohde(oid: HakukohdeOid): Option[HakukohdeRecord] = {
    runBlocking(sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
           from hakukohteet
           where hakukohde_oid = $oid
         """.as[HakukohdeRecord]).headOption
  }

  override def findHaunArbitraryHakukohde(oid: HakuOid): Option[HakukohdeRecord] = {
    runBlocking(sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
           from hakukohteet
           where haku_oid = $oid
           limit 1
         """.as[HakukohdeRecord]).headOption
  }

  override def findHaunHakukohteet(oid: HakuOid): Set[HakukohdeRecord] = {
    runBlocking(sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
           from hakukohteet
           where haku_oid = $oid
         """.as[HakukohdeRecord]).toSet
  }

  override def all: Set[HakukohdeRecord] = {
    runBlocking(
      sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
            from hakukohteet""".as[HakukohdeRecord]).toSet
  }

  override def findHakukohteet(hakukohdeOids: Set[HakukohdeOid]): Set[HakukohdeRecord] = hakukohdeOids match {
    case x if 0 == x.size => Set()
    case _ => {
      val invalidOids = hakukohdeOids.filterNot(_.valid)
      if (invalidOids.nonEmpty) {
        throw new IllegalArgumentException(s"${invalidOids.size} huonoa oidia syötteessä: $invalidOids")
      }
      val inParameter = hakukohdeOids.map(oid => s"'$oid'").mkString(",")
      runBlocking(
        sql"""select hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi
            from hakukohteet where hakukohde_oid in (#$inParameter)""".as[HakukohdeRecord]).toSet
    }
  }

  override def storeHakukohde(hakukohdeRecord: HakukohdeRecord): Unit = {
    val UNIQUE_VIOLATION = "23505"
    val koulutuksenAlkamiskausi = hakukohdeRecord match {
      case h: EiKktutkintoonJohtavaHakukohde => h.koulutuksenAlkamiskausi
      case h: EiYPSHakukohde => Some(h.koulutuksenAlkamiskausi)
      case h: YPSHakukohde => Some(h.koulutuksenAlkamiskausi)
    }
    try {
      runBlocking(DBIO.seq(
        sqlu"""insert into hakukohteet (hakukohde_oid, haku_oid, yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi)
               values (${hakukohdeRecord.oid},
                       ${hakukohdeRecord.hakuOid},
                       ${hakukohdeRecord.yhdenPaikanSaantoVoimassa},
                       ${hakukohdeRecord.kktutkintoonJohtava},
                       ${koulutuksenAlkamiskausi.map(_.toKausiSpec)})""",

        DBIO.sequenceOption(koulutuksenAlkamiskausi.map(alkamiskausi =>
          sqlu"""insert into koulutuksen_alkamiskausi (hakukohde_oid, koulutuksen_alkamiskausi)
                 values (${hakukohdeRecord.oid}, ${alkamiskausi.toKausiSpec})
                 on conflict (hakukohde_oid) do nothing""")),

        if (hakukohdeRecord.kktutkintoonJohtava) {
          sqlu"""insert into kk_tutkintoon_johtava (hakukohde_oid)
                 values (${hakukohdeRecord.oid})
                 on conflict (hakukohde_oid) do nothing"""
        } else { DBIO.successful(0) },

        if (hakukohdeRecord.yhdenPaikanSaantoVoimassa) {
          sqlu"""insert into yhden_paikan_saanto_voimassa (hakukohde_oid)
                 values (${hakukohdeRecord.oid})
                 on conflict (hakukohde_oid) do nothing"""
        } else { DBIO.successful(0) }))
    } catch {
      case e: PSQLException if e.getSQLState == UNIQUE_VIOLATION =>
        logger.debug(s"Ignored unique violation when inserting hakukohde record $hakukohdeRecord")
    }
  }

  override def updateHakukohde(hakukohdeRecord: HakukohdeRecord): Boolean = {
    val koulutuksenAlkamiskausi = hakukohdeRecord match {
      case h: EiKktutkintoonJohtavaHakukohde => h.koulutuksenAlkamiskausi
      case h: EiYPSHakukohde => Some(h.koulutuksenAlkamiskausi)
      case h: YPSHakukohde => Some(h.koulutuksenAlkamiskausi)
    }
    runBlocking(
      DBIO.seq(
        DBIO.sequenceOption(koulutuksenAlkamiskausi.map(alkamiskausi =>
          sqlu"""insert into koulutuksen_alkamiskausi (hakukohde_oid, koulutuksen_alkamiskausi)
                 values (${hakukohdeRecord.oid}, ${alkamiskausi.toKausiSpec})
                 on conflict (hakukohde_oid) do nothing""")),

        if (hakukohdeRecord.kktutkintoonJohtava) {
          sqlu"""insert into kk_tutkintoon_johtava (hakukohde_oid)
                 values (${hakukohdeRecord.oid})
                 on conflict (hakukohde_oid) do nothing"""
        } else { DBIO.successful(0) },

        if (hakukohdeRecord.yhdenPaikanSaantoVoimassa) {
          sqlu"""insert into yhden_paikan_saanto_voimassa (hakukohde_oid)
                 values (${hakukohdeRecord.oid})
                 on conflict (hakukohde_oid) do nothing"""
        } else {
          sqlu"""delete from yhden_paikan_saanto_voimassa
                 where hakukohde_oid = ${hakukohdeRecord.oid}"""
        },

        if (!hakukohdeRecord.kktutkintoonJohtava) {
          sqlu"""delete from kk_tutkintoon_johtava
                 where hakukohde_oid = ${hakukohdeRecord.oid}"""
        } else { DBIO.successful(0) },

        if (koulutuksenAlkamiskausi.isEmpty) {
          sqlu"""delete from koulutuksen_alkamiskausi
                 where hakukohde_oid = ${hakukohdeRecord.oid}"""
        } else { DBIO.successful(0) })
        .andThen(
          sqlu"""update hakukohteet set (yhden_paikan_saanto_voimassa, kk_tutkintoon_johtava, koulutuksen_alkamiskausi)
                   = (${hakukohdeRecord.yhdenPaikanSaantoVoimassa},
                      ${hakukohdeRecord.kktutkintoonJohtava},
                      ${koulutuksenAlkamiskausi.map(_.toKausiSpec)})
                 where hakukohde_oid = ${hakukohdeRecord.oid} and
                       (yhden_paikan_saanto_voimassa <> ${hakukohdeRecord.yhdenPaikanSaantoVoimassa} or
                        kk_tutkintoon_johtava <> ${hakukohdeRecord.kktutkintoonJohtava} or
                        koulutuksen_alkamiskausi is distinct from ${koulutuksenAlkamiskausi.map(_.toKausiSpec)})""")
    ) == 1
  }

  override def hakukohteessaVastaanottoja(oid: HakukohdeOid): Boolean = {
    runBlocking(sql"""select count(*) from newest_vastaanotot where hakukohde = ${oid}""".as[Int]).head > 0
  }
}
