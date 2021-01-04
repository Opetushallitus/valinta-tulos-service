package fi.vm.sade.valintatulosservice.valintarekisteri.db.impl

import java.time.ZonedDateTime

import fi.vm.sade.valintatulosservice.valintarekisteri.db.{Valintaesitys, ValintaesitysRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakukohdeOid, ValintatapajonoOid}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait ValintaesitysRepositoryImpl extends ValintaesitysRepository with ValintarekisteriRepository {
  override def get(valintatapajonoOid: ValintatapajonoOid): DBIO[Option[Valintaesitys]] = {
    sql"""select hakukohde_oid, hyvaksytty
          from valintaesitykset
          where valintatapajono_oid = $valintatapajonoOid
      """.as[(HakukohdeOid, Option[ZonedDateTime])].map {
      case Vector() => None
      case (hakukohdeOid, hyvaksytty) +: Vector() =>
        Some(Valintaesitys(hakukohdeOid, valintatapajonoOid, hyvaksytty))
      case v =>
        throw new RuntimeException(
          s"Multiple results ($v) for primary key. Are we missing a constraint?"
        )
    }
  }

  override def get(hakukohdeOid: HakukohdeOid): DBIO[Set[Valintaesitys]] = {
    sql"""select valintatapajono_oid, hyvaksytty
          from valintaesitykset
          where hakukohde_oid = $hakukohdeOid
      """
      .as[(ValintatapajonoOid, Option[ZonedDateTime])]
      .map(_.map {
        case (valintatapajonoOid, hyvaksytty) =>
          Valintaesitys(hakukohdeOid, valintatapajonoOid, hyvaksytty)
      }.toSet)
  }

  override def hyvaksyValintaesitys(valintatapajonoOid: ValintatapajonoOid): DBIO[Valintaesitys] = {
    sqlu"""update valintaesitykset set hyvaksytty = now()
           where valintatapajono_oid = $valintatapajonoOid
               and hyvaksytty is null
      """
      .flatMap(updated => get(valintatapajonoOid).map((updated == 1, _)))
      .flatMap {
        case (false, None) =>
          DBIO.failed(
            new IllegalStateException(
              s"Valintatapajonolla $valintatapajonoOid ei ole valintaesitystä."
            )
          )
        case (false, Some(Valintaesitys(_, _, None))) =>
          DBIO.failed(
            new RuntimeException(
              "No update, but valintaesitys still not hyväksytty. Are we not using isolation level serializable?"
            )
          )
        case (true, None) =>
          DBIO.failed(
            new RuntimeException(
              "Updated, but valintaesitys no longer exists. Are we not using isolation level serializable?"
            )
          )
        case (_, Some(valintaesitys)) =>
          DBIO.successful(valintaesitys)
      }
      .transactionally
  }
}
