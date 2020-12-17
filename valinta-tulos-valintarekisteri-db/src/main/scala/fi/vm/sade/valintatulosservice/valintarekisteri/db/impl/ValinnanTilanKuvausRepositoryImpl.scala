package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnanTilanKuvausRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{
  HakemusOid,
  HakukohdeOid,
  ValinnantilanTarkenne,
  ValintatapajonoOid
}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait ValinnanTilanKuvausRepositoryImpl
    extends ValinnanTilanKuvausRepository
    with ValintarekisteriRepository {
  override def storeValinnanTilanKuvaus(
    hakukohdeOid: HakukohdeOid,
    valintatapajonoOid: ValintatapajonoOid,
    hakemusOid: HakemusOid,
    valinnantilanTarkenne: ValinnantilanTarkenne,
    valinnantilanKuvauksenTekstiFI: Option[String],
    valinnantilanKuvauksenTekstiSV: Option[String],
    valinnantilanKuvauksenTekstiEN: Option[String]
  ): DBIO[Unit] = {
    getTilankuvausHash(hakukohdeOid, valintatapajonoOid, hakemusOid).flatMap(previousHashCode => {
      insertKuvaukset(
        valinnantilanTarkenne,
        valinnantilanKuvauksenTekstiFI,
        valinnantilanKuvauksenTekstiSV,
        valinnantilanKuvauksenTekstiEN
      )
        .flatMap(newHashCode =>
          upsertTilatKuvaukset(hakukohdeOid, valintatapajonoOid, hakemusOid, newHashCode)
        )
        .flatMap(wasUpsert =>
          (wasUpsert, previousHashCode) match {
            case (true, Some(possiblyOrphanHashCode)) =>
              deleteOrphanKuvaukset(possiblyOrphanHashCode)
            case _ => DBIO.successful(())
          }
        )
    })
  }

  private def getTilankuvausHash(
    hakukohdeOid: HakukohdeOid,
    valintatapajonoOid: ValintatapajonoOid,
    hakemusOid: HakemusOid
  ): DBIO[Option[Int]] = {
    sql"""select tilankuvaus_hash
          from tilat_kuvaukset
          where hakukohde_oid = ${hakukohdeOid} and
                valintatapajono_oid = ${valintatapajonoOid} and
                hakemus_oid = ${hakemusOid}
       """.as[Int].map(_.headOption)
  }

  private def insertKuvaukset(
    valinnantilanTarkenne: ValinnantilanTarkenne,
    valinnantilanKuvauksenTekstiFI: Option[String],
    valinnantilanKuvauksenTekstiSV: Option[String],
    valinnantilanKuvauksenTekstiEN: Option[String]
  ): DBIO[Int] = {
    val valinnanTilanKuvausHashCode = tilanKuvauksetHashCode(
      valinnantilanKuvauksenTekstiFI,
      valinnantilanKuvauksenTekstiSV,
      valinnantilanKuvauksenTekstiEN
    )
    sqlu"""insert into valinnantilan_kuvaukset (
               hash,
               tilan_tarkenne,
               text_fi,
               text_sv,
               text_en
           ) values (
               ${valinnanTilanKuvausHashCode},
               ${valinnantilanTarkenne}::valinnantilantarkenne,
               ${valinnantilanKuvauksenTekstiFI},
               ${valinnantilanKuvauksenTekstiSV},
               ${valinnantilanKuvauksenTekstiEN}
           ) on conflict on constraint valinnantilan_kuvaukset_pkey
           do update set
               tilan_tarkenne = excluded.tilan_tarkenne,
               text_fi = excluded.text_fi,
               text_sv = excluded.text_sv,
               text_en = excluded.text_en
           where valinnantilan_kuvaukset.text_fi is not distinct from excluded.text_fi and
                 valinnantilan_kuvaukset.text_sv is not distinct from excluded.text_sv and
                 valinnantilan_kuvaukset.text_en is not distinct from excluded.text_en
      """.flatMap {
      case 1 => DBIO.successful(valinnanTilanKuvausHashCode)
      case 0 =>
        DBIO.failed(
          new RuntimeException(
            s"Hash-koodi törmäys tallennettaessa tilan kuvauksia $valinnantilanKuvauksenTekstiFI, $valinnantilanKuvauksenTekstiSV, $valinnantilanKuvauksenTekstiEN, hash-koodilla $valinnanTilanKuvausHashCode"
          )
        )
      case n =>
        DBIO.failed(
          new RuntimeException(
            s"Odottamaton päivitysten määrä $n tallennettaessa tilan kuvauksia $valinnantilanKuvauksenTekstiFI, $valinnantilanKuvauksenTekstiSV, $valinnantilanKuvauksenTekstiEN, hash-koodilla $valinnanTilanKuvausHashCode"
          )
        )
    }
  }

  private def upsertTilatKuvaukset(
    hakukohdeOid: HakukohdeOid,
    valintatapajonoOid: ValintatapajonoOid,
    hakemusOid: HakemusOid,
    valinnanTilanKuvausHashCode: Int
  ): DBIO[Boolean] = {
    sqlu"""insert into tilat_kuvaukset (
               tilankuvaus_hash,
               hakukohde_oid,
               valintatapajono_oid,
               hakemus_oid
           ) values (
               ${valinnanTilanKuvausHashCode},
               ${hakukohdeOid},
               ${valintatapajonoOid},
               ${hakemusOid}
           ) on conflict on constraint tilat_kuvaukset_pkey
           do update set
               tilankuvaus_hash = excluded.tilankuvaus_hash
           where tilat_kuvaukset.tilankuvaus_hash <> excluded.tilankuvaus_hash
      """.flatMap {
      case 1 => DBIO.successful(true)
      case 0 => DBIO.successful(false)
      case n =>
        DBIO.failed(
          new RuntimeException(
            s"Odottamaton päivitysten määrä $n tallennettaessa tilat_kuvaukset riviä hash-koodilla $valinnanTilanKuvausHashCode hakukohteelle $hakukohdeOid, valintatapajonolle $valintatapajonoOid ja hakemukselle $hakemusOid"
          )
        )
    }
  }

  private def deleteOrphanKuvaukset(valinnanTilanKuvausHashCode: Int): DBIO[Unit] = {
    sqlu"""delete from valinnantilan_kuvaukset vk
           where vk.hash = ${valinnanTilanKuvausHashCode} and
                 not exists (select 1 from tilat_kuvaukset tk where tk.tilankuvaus_hash = vk.hash)
      """.map(_ => ())
  }

  private def tilanKuvauksetHashCode(
    valinnantilanKuvauksenTekstiFI: Option[String],
    valinnantilanKuvauksenTekstiSV: Option[String],
    valinnantilanKuvauksenTekstiEN: Option[String]
  ): Int = {
    val m = new util.HashMap[String, String]()
    valinnantilanKuvauksenTekstiFI.foreach(m.put("FI", _))
    valinnantilanKuvauksenTekstiSV.foreach(m.put("SV", _))
    valinnantilanKuvauksenTekstiEN.foreach(m.put("EN", _))
    m.hashCode()
  }
}
