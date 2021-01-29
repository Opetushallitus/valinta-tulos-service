package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.valintatulosservice.valintarekisteri.db.VastaanottoRecord

case class PriorAcceptanceException(aiempiVastaanotto: VastaanottoRecord)
  extends IllegalArgumentException(s"Löytyi aiempi vastaanotto $aiempiVastaanotto")

case class ConflictingAcceptancesException(personOid: String, conflictingVastaanottos: Seq[VastaanottoRecord], conflictDescription: String)
  extends IllegalStateException(s"Hakijalla $personOid useita vastaanottoja $conflictDescription: $conflictingVastaanottos")
