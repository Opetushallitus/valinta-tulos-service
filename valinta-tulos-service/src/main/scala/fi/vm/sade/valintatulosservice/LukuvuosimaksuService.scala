package fi.vm.sade.valintatulosservice

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.LukuvuosimaksuRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakijaOid, HakukohdeOid, Lukuvuosimaksu}

class LukuvuosimaksuService(lukuvuosimaksuRepository: LukuvuosimaksuRepository,
                            audit: Audit
                           ) extends Logging {

  def getLukuvuosimaksut(hakukohdeOid: HakukohdeOid, auditInfo: AuditInfo): Seq[Lukuvuosimaksu] = {
    val result = lukuvuosimaksuRepository.getLukuvuosimaksus(hakukohdeOid)
    audit.log(auditInfo.user, LukuvuosimaksujenLuku,
      new Target.Builder()
        .setField("hakukohde", hakukohdeOid.toString)
        .setField("muokkaaja", "")
        .build(),
      new Changes.Builder().build()
    )
    filterRelevantMaksusOfEachperson(result)
  }

  def getLukuvuosimaksut(hakukohdeOids: Set[HakukohdeOid], auditInfo: AuditInfo): Seq[Lukuvuosimaksu] = {
    val result = lukuvuosimaksuRepository.getLukuvuosimaksus(hakukohdeOids)
    audit.log(auditInfo.user, LukuvuosimaksujenLuku,
      new Target.Builder()
        .setField("hakukohde", hakukohdeOids.mkString(","))
        .setField("muokkaaja", "")
        .build(),
      new Changes.Builder().build()
    )
    filterRelevantMaksusOfEachperson(result)
  }

  def getLukuvuosimaksuByHakijaAndHakukohde(hakijaOid: HakijaOid, hakukohdeOid: HakukohdeOid, auditInfo: AuditInfo): Option[Lukuvuosimaksu] = {
    audit.log(auditInfo.user, LukuvuosimaksujenLuku,
      new Target.Builder()
        .setField("hakukohde", hakukohdeOid.toString)
        .setField("hakija", hakijaOid.toString)
        .setField("muokkaaja", "")
        .build(),
      new Changes.Builder().build()
    )
    lukuvuosimaksuRepository.getLukuvuosimaksuByHakijaAndHakukohde(hakijaOid, hakukohdeOid)
  }

  private def filterRelevantMaksusOfEachperson(result: List[Lukuvuosimaksu]) = {
    result
      .groupBy(l => l.personOid).values
      .map(l => l.sortBy(a => a.luotu).reverse)
      .map(l => l.head).toList
  }

  def updateLukuvuosimaksut(lukuvuosimaksut: List[Lukuvuosimaksu], auditInfo: AuditInfo) = {
    lukuvuosimaksuRepository.update(lukuvuosimaksut)
    lukuvuosimaksut.foreach(m => {
      audit.log(auditInfo.user, LukuvuosimaksujenMuokkaus,
        new Target.Builder()
          .setField("henkilö", m.personOid)
          .setField("hakukohde", m.hakukohdeOid.toString)
          .setField("maksuntila", m.maksuntila.toString)
          .setField("muokkaaja", m.muokkaaja)
          .setField("luotu", m.luotu.toString)
          .build(),
        new Changes.Builder().build()
      )
    })
  }
}
