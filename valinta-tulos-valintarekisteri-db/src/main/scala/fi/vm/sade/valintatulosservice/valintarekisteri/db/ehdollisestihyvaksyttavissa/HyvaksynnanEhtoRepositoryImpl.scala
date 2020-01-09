package fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa

import java.time.Instant
import java.util.ConcurrentModificationException

import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, ValintatapajonoOid}
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

trait HyvaksynnanEhtoRepositoryImpl extends HyvaksynnanEhtoRepository {
  def hyvaksynnanEhtoHakukohteessa(hakukohdeOid: HakukohdeOid): DBIO[List[(HakemusOid, HyvaksynnanEhto, Instant)]] = {
    sql"""select hakemus_oid,
                 koodi,
                 fi,
                 sv,
                 en,
                 lower(system_time)
          from hyvaksynnan_ehto_hakukohteessa
          where hakukohde_oid = $hakukohdeOid and
                not exists (select 1
                            from valinnantilat
                            where hakemus_oid = hyvaksynnan_ehto_hakukohteessa.hakemus_oid and
                                  hakukohde_oid = hyvaksynnan_ehto_hakukohteessa.hakukohde_oid)
      """.as[(HakemusOid, String, String, String, String, Option[Instant])].map(_.map {
      case (hakemusOid, koodi, fi, sv, en, Some(lastModified)) =>
        (hakemusOid, HyvaksynnanEhto(koodi, fi, sv, en), lastModified)
    }.toList)
  }

  def hyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[Option[(HyvaksynnanEhto, Instant)]] = {
    sql"""(select koodi,
                  fi,
                  sv,
                  en,
                  lower(system_time)
           from hyvaksynnan_ehto_hakukohteessa
           where hakemus_oid = $hakemusOid and
                 hakukohde_oid = $hakukohdeOid and
                 not exists (select 1
                             from valinnantilat
                             where hakemus_oid = $hakemusOid and
                                   hakukohde_oid = $hakukohdeOid))
          union
          (select '', '', '', '', null
           from valinnantilat
           where hakemus_oid = $hakemusOid and
                 hakukohde_oid = $hakukohdeOid
           limit 1)
      """.as[(String, String, String, String, Option[Instant])].headOption.map {
      case Some((koodi, fi, sv, en, Some(lastModified))) =>
        Some((HyvaksynnanEhto(koodi, fi, sv, en), lastModified))
      case Some((_, _, _, _, None)) =>
        throw new GoneException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid ei ole koska valinnan tulos on jo olemassa")
      case None =>
        None
    }
  }

  def hyvaksynnanEhdotValintatapajonoissa(hakukohdeOid: HakukohdeOid): DBIO[List[(HakemusOid, ValintatapajonoOid, HyvaksynnanEhto, Instant)]] = {
    sql"""select hakemus_oid,
                 valintatapajono_oid,
                 ehdollisen_hyvaksymisen_ehto_koodi,
                 ehdollisen_hyvaksymisen_ehto_fi,
                 ehdollisen_hyvaksymisen_ehto_sv,
                 ehdollisen_hyvaksymisen_ehto_en,
                 lower(system_time)
          from ehdollisen_hyvaksynnan_ehto
          where hakukohde_oid = $hakukohdeOid
       """.as[(HakemusOid, ValintatapajonoOid, String, String, String, String, Instant)].map(_.map {
      case (hakemusOid, valintatapajonoOid, koodi, fi, sv, en, lastModified) =>
        (hakemusOid, valintatapajonoOid, HyvaksynnanEhto(koodi, fi, sv, en), lastModified)
    }.toList)
  }

  def hyvaksynnanEhdotValintatapajonoissa(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[List[(ValintatapajonoOid, HyvaksynnanEhto, Instant)]] = {
    sql"""select valintatapajono_oid,
                 ehdollisen_hyvaksymisen_ehto_koodi,
                 ehdollisen_hyvaksymisen_ehto_fi,
                 ehdollisen_hyvaksymisen_ehto_sv,
                 ehdollisen_hyvaksymisen_ehto_en,
                 lower(system_time)
          from ehdollisen_hyvaksynnan_ehto
          where hakemus_oid = $hakemusOid and
                hakukohde_oid = $hakukohdeOid
       """.as[(ValintatapajonoOid, String, String, String, String, Instant)].map(_.map {
      case (valintatapajonoOid, koodi, fi, sv, en, lastModified) =>
        (valintatapajonoOid, HyvaksynnanEhto(koodi, fi, sv, en), lastModified)
    }.toList)
  }

  def insertHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid,
                                         hakukohdeOid: HakukohdeOid,
                                         ehto: HyvaksynnanEhto,
                                         ilmoittaja: String): DBIO[(HyvaksynnanEhto, Instant)] = {
    sql"""insert into hyvaksynnan_ehto_hakukohteessa (
            hakemus_oid,
            hakukohde_oid,
            koodi,
            fi,
            sv,
            en,
            ilmoittaja
          ) values (
            $hakemusOid,
            $hakukohdeOid,
            ${ehto.koodi},
            ${ehto.fi},
            ${ehto.sv},
            ${ehto.en},
            $ilmoittaja
          )
          on conflict (hakemus_oid, hakukohde_oid) do nothing
          returning koodi,
                    fi,
                    sv,
                    en,
                    case (select true
                          from valinnantilat
                          where hakemus_oid = $hakemusOid and
                                hakukohde_oid = $hakukohdeOid
                          limit 1)
                      when true then null
                      else lower(system_time)
                    end
      """.as[(String, String, String, String, Option[Instant])].headOption.map {
      case Some((koodi, fi, sv, en, Some(lastModified))) =>
        (HyvaksynnanEhto(koodi, fi, sv, en), lastModified)
      case Some((_, _, _, _, None)) =>
        throw new GoneException(s"Hakemukselle $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid ei voi tallentaa koska valinnan tulos on jo olemassa")
      case None =>
        throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid oli päivitetty samanaikaisesti")
    }.transactionally
  }

  def updateHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid,
                                         hakukohdeOid: HakukohdeOid,
                                         ehto: HyvaksynnanEhto,
                                         ilmoittaja: String,
                                         ifUnmodifiedSince: Instant): DBIO[(HyvaksynnanEhto, Instant)] = {
    sql"""update hyvaksynnan_ehto_hakukohteessa
          set koodi = ${ehto.koodi},
              fi = ${ehto.fi},
              sv = ${ehto.sv},
              en = ${ehto.en},
              ilmoittaja = $ilmoittaja
          where hakemus_oid = $hakemusOid and
                hakukohde_oid = $hakukohdeOid and
                (koodi <> ${ehto.koodi} or
                 fi <> ${ehto.fi} or
                 sv <> ${ehto.sv} or
                 en <> ${ehto.en}) and
                system_time @> $ifUnmodifiedSince
          returning koodi,
                    fi,
                    sv,
                    en,
                    case (select true
                          from valinnantilat
                          where hakemus_oid = $hakemusOid and
                                hakukohde_oid = $hakukohdeOid
                          limit 1)
                      when true then null
                      else lower(system_time)
                    end
      """.as[(String, String, String, String, Option[Instant])].headOption.map {
      case Some((koodi, fi, sv, en, Some(lastModified))) =>
        (HyvaksynnanEhto(koodi, fi, sv, en), lastModified)
      case Some((_, _, _, _, None)) =>
        throw new GoneException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid ei voi muokata koska valinnan tulos on jo olemassa")
      case None =>
        throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid oli päivitetty samanaikaisesti")
    }.transactionally
  }

  def deleteHyvaksynnanEhtoHakukohteessa(hakemusOid: HakemusOid,
                                         hakukohdeOid: HakukohdeOid,
                                         ifUnmodifiedSince: Instant): DBIO[HyvaksynnanEhto] = {
    sql"""delete from hyvaksynnan_ehto_hakukohteessa
          where hakemus_oid = $hakemusOid and
                hakukohde_oid = $hakukohdeOid and
                system_time @> $ifUnmodifiedSince
          returning koodi,
                    fi,
                    sv,
                    en,
                    case (select true
                          from valinnantilat
                          where hakemus_oid = $hakemusOid and
                                hakukohde_oid = $hakukohdeOid
                          limit 1)
                      when true then null
                      else lower(system_time)
                    end
      """.as[(String, String, String, String, Option[Instant])].headOption.map {
      case Some((koodi, fi, sv, en, Some(_))) =>
        HyvaksynnanEhto(koodi, fi, sv, en)
      case Some((_, _, _, _, None)) =>
        throw new GoneException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid ei voi poistaa koska valinnan tulos on jo olemassa")
      case None =>
        throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteessa $hakukohdeOid oli päivitetty samanaikaisesti")
    }.transactionally
  }

  def hyvaksynnanEhtoHakukohteessaMuutoshistoria(hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid): DBIO[List[Versio[HyvaksynnanEhto]]] = {
    sql"""((select koodi,
                   fi,
                   sv,
                   en,
                   lower(system_time) as lower_st,
                   null,
                   ilmoittaja
            from hyvaksynnan_ehto_hakukohteessa
            where hakemus_oid = $hakemusOid and
                  hakukohde_oid = $hakukohdeOid)
           union all
           (select koodi,
                   fi,
                   sv,
                   en,
                   lower(system_time) as lower_st,
                   upper(system_time),
                   ilmoittaja
            from hyvaksynnan_ehto_hakukohteessa_history
            where hakemus_oid = $hakemusOid and
                  hakukohde_oid = $hakukohdeOid)
           order by lower_st asc)
      """.as[(String, String, String, String, Instant, Option[Instant], String)].map(_.toList.map {
      case (koodi, fi, sv, en, alku, None, ilmoittaja) =>
        Nykyinen(HyvaksynnanEhto(koodi, fi, sv, en), alku, ilmoittaja)
      case (koodi, fi, sv, en, alku, Some(loppu), ilmoittaja) =>
        Edellinen(HyvaksynnanEhto(koodi, fi, sv, en), alku, loppu, ilmoittaja)
    } match {
      case Nil => Nil
      case h :: hs =>
        hs.foldLeft(List(h)) {
          case (hs@Versio((_, Some(loppu))) :: _, h@Versio((alku, _))) if loppu != alku => h :: EdellinenPoistettu(loppu, alku) :: hs
          case (hs, h) => h :: hs
        } match {
          case hs@Versio((_, Some(loppu))) :: _ => NykyinenPoistettu(loppu) :: hs
          case hs => hs
        }
    })
  }

  def insertHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid,
                                              valintatapajonoOid: ValintatapajonoOid,
                                              hakukohdeOid: HakukohdeOid,
                                              ehto: HyvaksynnanEhto): DBIO[Unit] = {
    sqlu"""insert into ehdollisen_hyvaksynnan_ehto (
             hakemus_oid,
             valintatapajono_oid,
             hakukohde_oid,
             ehdollisen_hyvaksymisen_ehto_koodi,
             ehdollisen_hyvaksymisen_ehto_fi,
             ehdollisen_hyvaksymisen_ehto_sv,
             ehdollisen_hyvaksymisen_ehto_en
           ) values (
             $hakemusOid,
             $valintatapajonoOid,
             $hakukohdeOid,
             ${ehto.koodi},
             ${ehto.fi},
             ${ehto.sv},
             ${ehto.en}
           )
           on conflict do nothing
      """.flatMap {
      case 1 => DBIO.successful(())
      case _ => throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteen $hakukohdeOid valintatapajonossa $valintatapajonoOid oli päivitetty samanaikaisesti")
    }
  }

  def updateHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid,
                                              valintatapajonoOid: ValintatapajonoOid,
                                              hakukohdeOid: HakukohdeOid,
                                              ehto: HyvaksynnanEhto,
                                              ifUnmodifiedSince: Instant): DBIO[Unit] = {
    sqlu"""update ehdollisen_hyvaksynnan_ehto
           set ehdollisen_hyvaksymisen_ehto_koodi = ${ehto.koodi},
               ehdollisen_hyvaksymisen_ehto_fi = ${ehto.fi},
               ehdollisen_hyvaksymisen_ehto_sv = ${ehto.sv},
               ehdollisen_hyvaksymisen_ehto_en = ${ehto.en}
           where hakemus_oid = $hakemusOid and
                 valintatapajono_oid = $valintatapajonoOid and
                 hakukohde_oid = $hakukohdeOid and
                 (ehdollisen_hyvaksymisen_ehto_koodi <> ${ehto.koodi} or
                  ehdollisen_hyvaksymisen_ehto_fi <> ${ehto.fi} or
                  ehdollisen_hyvaksymisen_ehto_sv <> ${ehto.sv} or
                  ehdollisen_hyvaksymisen_ehto_en <> ${ehto.en}) and
                 system_time @> $ifUnmodifiedSince
      """.flatMap {
      case 1 => DBIO.successful(())
      case _ => throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteen $hakukohdeOid valintatapajonossa $valintatapajonoOid oli päivitetty samanaikaisesti")
    }
  }

  def deleteHyvaksynnanEhtoValintatapajonossa(hakemusOid: HakemusOid,
                                              valintatapajonoOid: ValintatapajonoOid,
                                              hakukohdeOid: HakukohdeOid,
                                              ifUnmodifiedSince: Instant): DBIO[Unit] = {
    sqlu"""delete from ehdollisen_hyvaksynnan_ehto
           where hakemus_oid = $hakemusOid and
                 valintatapajono_oid = $valintatapajonoOid and
                 hakukohde_oid = $hakukohdeOid and
                 system_time @> $ifUnmodifiedSince
       """.flatMap {
      case 1 => DBIO.successful(())
      case _ => throw new ConcurrentModificationException(s"Hakemuksen $hakemusOid hyväksynnän ehtoa hakukohteen $hakukohdeOid valintatapajonossa $valintatapajonoOid oli päivitetty samanaikaisesti")
    }
  }
}
