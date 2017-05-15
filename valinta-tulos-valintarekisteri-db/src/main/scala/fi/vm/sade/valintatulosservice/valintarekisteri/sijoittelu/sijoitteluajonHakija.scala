package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.domain.SijoitteluAjo
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakutoiveDTO, HakutoiveenValintatapajonoDTO}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakemusRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

class SijoitteluajonHakija(val repository: HakemusRepository with SijoitteluRepository with ValinnantulosRepository,
                           val sijoitteluajoId:Option[Long],
                           val hakuOid:HakuOid,
                           val hakemusOid:HakemusOid) {

  def this(repository: HakemusRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajoId: String, hakuOid: HakuOid, hakemusOid: HakemusOid) {
    this(repository, Some(repository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)), hakuOid, hakemusOid)
  }

  def this(repository: HakemusRepository with SijoitteluRepository with ValinnantulosRepository, sijoitteluajo: SijoitteluAjo, hakemusOid: HakemusOid) {
    this(repository, SyntheticSijoitteluAjoForHakusWithoutSijoittelu.getSijoitteluajoId(sijoitteluajo), HakuOid(sijoitteluajo.getHakuOid), hakemusOid)
  }

  val hakija = repository.getHakemuksenHakija(hakemusOid, sijoitteluajoId)
    .orElse(throw new IllegalArgumentException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $sijoitteluajoId")).get

  lazy val haunValinnantilat = repository.getHaunValinnantilat(hakuOid) //TODO performance? Do we need koko haku?

  lazy val hakemuksenValinnantulokset = repository.runBlocking(repository.getValinnantuloksetForHakemus(hakemusOid)).groupBy(_.hakukohdeOid) //.map(v => (v.hakukohdeOid, v.valintatapajonoOid) -> v).toMap

  lazy val hakutoiveetSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveetSijoittelussa(hakemusOid, _).map(h => h.hakukohdeOid -> h).toMap).getOrElse(Map())
  lazy val valintatapajonotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenValintatapajonotSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())
  lazy val pistetiedotSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenPistetiedotSijoittelussa(hakemusOid, _).groupBy(_.valintatapajonoOid)).getOrElse(Map())
  lazy val hakijaryhmatSijoittelussa = sijoitteluajoId.map(repository.getHakemuksenHakutoiveidenHakijaryhmatSijoittelussa(hakemusOid, _).groupBy(_.hakukohdeOid)).getOrElse(Map())

  lazy val tilankuvauksetSijoittelussa = repository.getValinnantilanKuvaukset(
    valintatapajonotSijoittelussa.values.flatten.map(_.tilankuvausHash).toList.distinct
  )

  def hyvaksyttyValintatapajonosta(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid) = {
    haunValinnantilat.filter{ case (hakukohde, valintatapajono, hakemus, tila) =>
      hakukohde.equals(hakukohdeOid) && valintatapajono.equals(valintatapajonoOid) && List(Hyvaksytty, VarasijaltaHyvaksytty).contains(tila)
    }.map(_._3).distinct.size
  }

  def hakeneetValintatapajonossa(hakukohdeOid: HakukohdeOid, valintatapajonoOid: ValintatapajonoOid) = {
    haunValinnantilat.filter{ case (hakukohde, valintatapajono, hakemus, tila) =>
      hakukohde.equals(hakukohdeOid) && valintatapajono.equals(valintatapajonoOid)
    }.map(_._3).distinct.size
  }

  def dto(): HakijaDTO = {
    val hakukohdeOidit = hakemuksenValinnantulokset.keySet.union(hakutoiveetSijoittelussa.keySet)
    hakija.dto(hakukohdeOidit.map { hakukohdeOid =>
      if (hakutoiveetSijoittelussa.contains(hakukohdeOid)) {
        val hakutoive = hakutoiveetSijoittelussa(hakukohdeOid)
        val valintatapajonot = valintatapajonotSijoittelussa.getOrElse(hakukohdeOid, List())
        val valintatapajonoOidit = valintatapajonot.map(_.valintatapajonoOid)
        val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
        val pistetiedot = pistetiedotSijoittelussa.filterKeys(valintatapajonoOidit.contains).values.flatten.map(HakutoiveenPistetietoRecord(_)).toList.distinct.map(_.dto)
        val hakijaryhmat = hakijaryhmatSijoittelussa.getOrElse(hakukohdeOid, List()).map(_.dto)
        val valintatapajonoDtot = valintatapajonot.map{ j =>
          j.dto(
            valinnantulokset.find(_.valintatapajonoOid.equals(j.valintatapajonoOid)),
            hyvaksyttyValintatapajonosta(hakukohdeOid, j.valintatapajonoOid),
            j.tilankuvaukset(tilankuvauksetSijoittelussa.get(j.tilankuvausHash)))
        }

        hakutoive.dto(
          valintatapajonoDtot,
          pistetiedot,
          hakijaryhmat
        )
      } else {
        val valinnantulokset = hakemuksenValinnantulokset.getOrElse(hakukohdeOid, List())
        val hakutoive = HakutoiveRecord(hakemusOid, None, hakukohdeOid, None) //TODO hakutoive=1?
        val valintatapajonoDtot = valinnantulokset.map{ j =>
          HakutoiveenValintatapajonoRecord.dto(
            j,
            hakeneetValintatapajonossa(hakukohdeOid, j.valintatapajonoOid),
            hyvaksyttyValintatapajonosta(hakukohdeOid, j.valintatapajonoOid)
          )
        }.toList
        hakutoive.dto(valintatapajonoDtot, List(), List())
      }
    }.toList)
  }
}