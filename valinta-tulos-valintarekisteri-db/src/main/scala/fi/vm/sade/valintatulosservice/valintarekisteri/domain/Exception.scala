package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord

case class PriorAcceptanceException(aiempiVastaanotto: VastaanottoRecord)
  extends IllegalArgumentException(s"Löytyi aiempi vastaanotto $aiempiVastaanotto")

case class ConflictingAcceptancesException(personOid: String, conflictingVastaanottos: Seq[VastaanottoRecord], conflictDescription: String)
  extends IllegalStateException(s"Hakijalla $personOid useita vastaanottoja $conflictDescription: $conflictingVastaanottos")

/**
 * Henkilöllä voi olla hakukohteessa vain yksi voimassa oleva vastaanotto ja yksi ilmoittautuminen. Jos se on tallennettu
 * henkilön toiselle hakemukselle samaan hakukohteeseen, tallennus hylätään eikä toisen hakemuksen tietoa ylikirjoiteta.
 */
case class ToisenHakemuksenTulosException(henkiloOid: String, hakukohdeOid: HakukohdeOid, hakemusOid: HakemusOid, toinenHakemusOid: HakemusOid, tulos: String)
  extends RuntimeException(s"$tulos on tallennettu henkilön $henkiloOid toiselle hakemukselle $toinenHakemusOid hakukohteeseen $hakukohdeOid, " +
    s"joten sitä ei voi tallentaa hakemukselle $hakemusOid. Poista se ensin toiselta hakemukselta.")

object ToisenHakemuksenTulosException {
  val Vastaanotto = "Vastaanotto"
  val Ilmoittautuminen = "Ilmoittautuminen"
}
