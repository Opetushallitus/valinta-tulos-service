package fi.vm.sade.valintatulosservice.valintarekisteri.domain

import fi.vm.sade.sijoittelu.domain.{HakukohdeItem, SijoitteluAjo}

case class SyntheticSijoitteluAjoForHakusWithoutSijoittelu(
  hakuOid: HakuOid,
  hakukohdeOidit: List[HakukohdeOid] = List()
) extends SijoitteluAjo {
  setHakuOid(hakuOid.toString)
  setSijoitteluajoId(SyntheticSijoitteluAjoForHakusWithoutSijoittelu.syntheticSijoitteluajoId)
  setStartMils(-1L)
  setEndMils(-1L)

  import scala.collection.JavaConverters._
  setHakukohteet(
    hakukohdeOidit.map(oid => SyntheticHakukohdeItem(oid).asInstanceOf[HakukohdeItem]).asJava
  )

  case class SyntheticHakukohdeItem(hakukohdeOid: HakukohdeOid) extends HakukohdeItem {
    setOid(hakukohdeOid.toString)
  }
}

object SyntheticSijoitteluAjoForHakusWithoutSijoittelu {
  final val syntheticSijoitteluajoId = -1L

  def isSynthetic(ajo: SijoitteluAjo) =
    ajo.isInstanceOf[
      SyntheticSijoitteluAjoForHakusWithoutSijoittelu
    ] || null == ajo.getSijoitteluajoId || 0 > ajo.getSijoitteluajoId

  def getSijoitteluajoId(ajo: SijoitteluAjo): Option[Long] =
    ajo match {
      case a if !isSynthetic(a) => Some(a.getSijoitteluajoId)
      case _                    => None
    }
}
