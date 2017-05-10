package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.valintarekisteri.db.SijoitteluRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakutoiveenPistetietoRecord}

class SijoitteluajonHakija(val sijoitteluRepository: SijoitteluRepository,
                           val sijoitteluajoId:String,
                           val hakuOid:HakuOid,
                           val hakemusOid:HakemusOid) {

  val latestId = sijoitteluRepository.getLatestSijoitteluajoIdThrowFailure(sijoitteluajoId, hakuOid)
  val hakija = sijoitteluRepository.getHakemuksenHakija(hakemusOid, latestId)
    .orElse(throw new IllegalArgumentException(s"Hakijaa ei löytynyt hakemukselle $hakemusOid, sijoitteluajoid: $latestId")).get

  lazy val hakutoiveet = sijoitteluRepository.getHakemuksenHakutoiveet(hakemusOid, latestId)
  lazy val valintatapajonot = sijoitteluRepository.getHakemuksenHakutoiveidenValintatapajonot(hakemusOid, latestId).groupBy(_.hakukohdeOid)
  lazy val pistetiedot = sijoitteluRepository.getHakemuksenPistetiedot(hakemusOid, latestId).groupBy(_.valintatapajonoOid)
  lazy val hakijaryhmat = sijoitteluRepository.getHakemuksenHakutoiveidenHakijaryhmat(hakemusOid, latestId).groupBy(_.hakukohdeOid)

  lazy val tilankuvaukset = sijoitteluRepository.getValinnantilanKuvaukset(
    valintatapajonot.values.flatten.map(_.tilankuvausHash).toList.distinct
  )

  def dto():HakijaDTO = {
    hakija.dto(
      hakutoiveet.map { h => {
        val (valintatapajonoOidit, valintatapajonoDtot) = {
          val jonot = valintatapajonot.getOrElse(h.hakukohdeOid, List())
          (jonot.map(_.valintatapajonoOid), jonot.map(v => v.dto(v.tilankuvaukset(tilankuvaukset.get(v.tilankuvausHash)))))
        }
        val hakutoiveenPistetiedot = pistetiedot.filterKeys(valintatapajonoOidit.contains).values.flatten.map(HakutoiveenPistetietoRecord(_)).toList.distinct.map(_.dto)
        val hakutoiveenHakijaryhmat = hakijaryhmat.getOrElse(h.hakukohdeOid, List()).map(_.dto)

        h.dto(
          valintatapajonoDtot,
          hakutoiveenPistetiedot,
          hakutoiveenHakijaryhmat
        )
      }}
    )
  }
}
