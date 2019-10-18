package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.util.ConcurrentModificationException

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnanTilanKuvausRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValinnanTilanKuvausHashCode, ValinnantilanTarkenne, ValintatapajonoOid}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait ValinnanTilanKuvausRepositoryImpl extends ValinnanTilanKuvausRepository with ValintarekisteriRepository {
  override def storeValinnanTilanKuvaus(
                                         valinnanTilanKuvausHashCode: ValinnanTilanKuvausHashCode,
                                         hakukohdeOid: HakukohdeOid,
                                         valintatapajonoOid: ValintatapajonoOid,
                                         hakemusOid: HakemusOid,
                                         valinnantilanTarkenne: ValinnantilanTarkenne,
                                         valinnantilanKuvauksenTekstiFI: Option[String],
                                         valinnantilanKuvauksenTekstiSV: Option[String],
                                         valinnantilanKuvauksenTekstiEN: Option[String]
                                       ): DBIO[Unit] = {
    sqlu"""delete from valinnantilan_kuvaukset vk
             where exists(select 1 from tilat_kuvaukset tk
                          where tk.tilankuvaus_hash = vk.hash
                            and tk.hakukohde_oid = ${hakukohdeOid}
                            and tk.valintatapajono_oid = ${valintatapajonoOid}
                            and tk.hakemus_oid = ${hakemusOid})
      """.flatMap(
      _ => DBIO.successful(())
    ).andThen(
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
           )
           on conflict on constraint tilat_kuvaukset_pkey do update set
             tilankuvaus_hash = excluded.tilankuvaus_hash
           where tilat_kuvaukset.tilankuvaus_hash <> excluded.tilankuvaus_hash
        """.flatMap {
        case 1 => DBIO.successful(())
        case 0 => DBIO.successful(())
        case _ => DBIO.failed(new ConcurrentModificationException(s"Ei voitu lisätä tilat_kuvaukset -riviä hash-koodilla ${valinnanTilanKuvausHashCode} hakukohteelle ${hakukohdeOid}, valintatapajonolle ${valintatapajonoOid} ja hakemukselle ${hakemusOid}"))
      }.andThen(
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
        """.flatMap(
          _ => DBIO.successful(())
        )
      )
    )
  }
}