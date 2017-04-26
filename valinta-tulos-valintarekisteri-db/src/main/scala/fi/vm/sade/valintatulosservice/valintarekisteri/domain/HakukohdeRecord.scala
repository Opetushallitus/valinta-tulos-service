package fi.vm.sade.valintatulosservice.valintarekisteri.domain

case class HakukohdeRecord(oid: HakukohdeOid, hakuOid: HakuOid, yhdenPaikanSaantoVoimassa: Boolean,
                           kktutkintoonJohtava: Boolean, koulutuksenAlkamiskausi: Kausi)
