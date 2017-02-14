package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.sql.Timestamp
import java.time.Instant
import java.util.ConcurrentModificationException
import java.util.concurrent.TimeUnit
import slick.driver.PostgresDriver.api._

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, ValinnantilanTallennus, ValinnantuloksenOhjaus, Valinnantulos}

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait ValinnantulosRepositoryImpl extends ValinnantulosRepository with ValintarekisteriRepository {

  override def getValinnantuloksetForValintatapajono(valintatapajonoOid: String, duration:Duration = Duration(1, TimeUnit.SECONDS)): List[(Instant, Valinnantulos)] = {
    runBlocking( sql"""select lower(tu.system_time),
              lower(ti.system_time),
              v.timestamp,
              lower(i.system_time),
              ti.hakukohde_oid,
              ti.valintatapajono_oid,
              ti.hakemus_oid,
              ti.henkilo_oid,
              ti.tila,
              tu.ehdollisesti_hyvaksyttavissa,
              tu.julkaistavissa,
              tu.hyvaksytty_varasijalta,
              tu.hyvaksy_peruuntunut,
              v.action,
              i.tila
          from valinnantilat as ti
          left join valinnantulokset as tu on tu.hakemus_oid = ti.hakemus_oid
              and tu.valintatapajono_oid = ti.valintatapajono_oid
          left join vastaanotot as v on v.hakukohde = tu.hakukohde_oid
              and v.henkilo = ti.henkilo_oid
          left join ilmoittautumiset as i on i.hakukohde = tu.hakukohde_oid
              and i.henkilo = ti.henkilo_oid
          where ti.valintatapajono_oid = ${valintatapajonoOid}
       """.as[(Instant, Valinnantulos)].map(_.toList), duration)
  }

  override def getTarjoajaForHakukohde(hakukohdeOid: String): String = {
    runBlocking(
      sql"""select tarjoaja_oid from sijoitteluajon_hakukohteet
            where hakukohde_oid = ${hakukohdeOid}
            order by sijoitteluajo_id desc limit 1""".as[String], Duration(1, TimeUnit.SECONDS)).head
  }

  override def getHakuForHakukohde(hakukohdeOid:String): String = {
    runBlocking(
      sql"""select a.haku_oid from sijoitteluajot a
            inner join sijoitteluajon_hakukohteet h on a.id = h.sijoitteluajo_id
            where h.hakukohde_oid = ${hakukohdeOid}
            order by sijoitteluajo_id desc limit 1""".as[String], Duration(1, TimeUnit.SECONDS)).head
  }

  override def updateValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""update valinnantulokset
           set julkaistavissa = ${ohjaus.julkaistavissa},
              ehdollisesti_hyvaksyttavissa = ${ohjaus.ehdollisestiHyvaksyttavissa},
              hyvaksytty_varasijalta = ${ohjaus.hyvaksyttyVarasijalta},
              hyvaksy_peruuntunut = ${ohjaus.hyvaksyPeruuntunut},
              ilmoittaja = ${ohjaus.muokkaaja},
              selite = ${ohjaus.selite}
           where valintatapajono_oid = ${ohjaus.valintatapajonoOid} and hakemus_oid = ${ohjaus.hakemusOid} and (
              julkaistavissa <> ${ohjaus.julkaistavissa} or
              ehdollisesti_hyvaksyttavissa <> ${ohjaus.ehdollisestiHyvaksyttavissa} or
              hyvaksytty_varasijalta <> ${ohjaus.hyvaksyttyVarasijalta} or
              hyvaksy_peruuntunut <> ${ohjaus.hyvaksyPeruuntunut}
           ) and (
              ${ifUnmodifiedSince}::timestamptz is null or
              system_time @> ${ifUnmodifiedSince}
           )""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti"))
    }
  }

  override def storeValinnantila(tila:ValinnantilanTallennus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    val tilanViimeisinMuutos = new Timestamp(System.currentTimeMillis)

    sqlu"""insert into valinnantilat(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ilmoittaja,
             henkilo_oid,
             tila,
             tilan_viimeisin_muutos
           ) values (${tila.valintatapajonoOid},
              ${tila.hakemusOid},
              ${tila.hakukohdeOid},
              ${tila.muokkaaja},
              ${tila.henkiloOid},
              ${tila.valinnantila.toString}::valinnantila,
              ${tilanViimeisinMuutos})
           on conflict on constraint valinnantilat_pkey do update set
             tila = excluded.tila,
             tilan_viimeisin_muutos = excluded.tilan_viimeisin_muutos,
             ilmoittaja = excluded.ilmoittaja
           where valinnantilat.tila <> excluded.tila
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              valinnantilat.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantilaa $tila ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti"))
    }
  }

  override def storeValinnantuloksenOhjaus(ohjaus:ValinnantuloksenOhjaus, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""insert into valinnantulokset(
             valintatapajono_oid,
             hakemus_oid,
             hakukohde_oid,
             ilmoittaja,
             selite,
             julkaistavissa,
             ehdollisesti_hyvaksyttavissa,
             hyvaksytty_varasijalta,
             hyvaksy_peruuntunut
           ) values (${ohjaus.valintatapajonoOid},
              ${ohjaus.hakemusOid},
              ${ohjaus.hakukohdeOid},
              ${ohjaus.muokkaaja},
              ${ohjaus.selite},
              ${ohjaus.julkaistavissa},
              ${ohjaus.ehdollisestiHyvaksyttavissa},
              ${ohjaus.hyvaksyttyVarasijalta},
              ${ohjaus.hyvaksyPeruuntunut})
           on conflict on constraint valinnantulokset_pkey do update set
             julkaistavissa = excluded.julkaistavissa,
             ilmoittaja = excluded.ilmoittaja,
             selite = excluded.selite,
             ehdollisesti_hyvaksyttavissa = excluded.ehdollisesti_hyvaksyttavissa,
             hyvaksytty_varasijalta = excluded.hyvaksytty_varasijalta,
             hyvaksy_peruuntunut = excluded.hyvaksy_peruuntunut
           where ( valinnantulokset.julkaistavissa <> excluded.julkaistavissa
             or valinnantulokset.ehdollisesti_hyvaksyttavissa <> excluded.ehdollisesti_hyvaksyttavissa
             or valinnantulokset.hyvaksytty_varasijalta <> excluded.hyvaksytty_varasijalta
             or valinnantulokset.hyvaksy_peruuntunut <> excluded.hyvaksy_peruuntunut )
             and (
              ${ifUnmodifiedSince}::timestamptz is null or
              valinnantulokset.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Valinnantuloksen ohjausta $ohjaus ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti"))
    }
  }

  override def storeIlmoittautuminen(henkiloOid: String, ilmoittautuminen: Ilmoittautuminen, ifUnmodifiedSince: Option[Instant] = None): DBIO[Unit] = {
    sqlu"""insert into ilmoittautumiset (henkilo, hakukohde, tila, ilmoittaja, selite)
             values (${henkiloOid},
                     ${ilmoittautuminen.hakukohdeOid},
                     ${ilmoittautuminen.tila.toString}::ilmoittautumistila,
                     ${ilmoittautuminen.muokkaaja},
                     ${ilmoittautuminen.selite})
             on conflict on constraint ilmoittautumiset_pkey do update
             set tila = excluded.tila,
                 ilmoittaja = excluded.ilmoittaja,
                 selite = excluded.selite
             where ilmoittautumiset.tila <> excluded.tila
                 and (${ifUnmodifiedSince}::timestamptz is null
                      or ilmoittautumiset.system_time @> ${ifUnmodifiedSince})""".flatMap {
      case 1 => DBIO.successful(())
      case _ => DBIO.failed(new ConcurrentModificationException(s"Ilmoittautumista $ilmoittautuminen ei voitu päivittää, koska joku oli muokannut sitä samanaikaisesti"))
    }
  }

}
