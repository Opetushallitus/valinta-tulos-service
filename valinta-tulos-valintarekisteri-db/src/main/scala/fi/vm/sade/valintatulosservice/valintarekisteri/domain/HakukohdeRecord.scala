package fi.vm.sade.valintatulosservice.valintarekisteri.domain

sealed trait HakukohdeRecord {
  def oid: HakukohdeOid
  def hakuOid: HakuOid
  def kktutkintoonJohtava: Boolean
  def yhdenPaikanSaantoVoimassa: Boolean
}

case class EiKktutkintoonJohtavaHakukohde(
  oid: HakukohdeOid,
  hakuOid: HakuOid,
  koulutuksenAlkamiskausi: Option[Kausi]
) extends HakukohdeRecord {
  override def kktutkintoonJohtava: Boolean = false
  override def yhdenPaikanSaantoVoimassa: Boolean = false
}

case class EiYPSHakukohde(oid: HakukohdeOid, hakuOid: HakuOid, koulutuksenAlkamiskausi: Kausi)
    extends HakukohdeRecord {
  override def kktutkintoonJohtava: Boolean = true
  override def yhdenPaikanSaantoVoimassa: Boolean = false
}

case class YPSHakukohde(oid: HakukohdeOid, hakuOid: HakuOid, koulutuksenAlkamiskausi: Kausi)
    extends HakukohdeRecord {
  override def kktutkintoonJohtava: Boolean = true
  override def yhdenPaikanSaantoVoimassa: Boolean = true
}
