package fi.vm.sade.valintatulosservice

import java.net.InetAddress
import java.util.UUID

import fi.vm.sade.auditlog.{Operation, User}
import fi.vm.sade.valintatulosservice.security.Session
import org.ietf.jgss.Oid

case class AuditInfo(session: (UUID, Session), ip: InetAddress, userAgent: String) {
  val user: User = new User(new Oid(session._2.personOid), ip, session._1.toString, userAgent)
}

case object ValinnantuloksenLuku extends Operation {
  def name: String = "VALINNANTULOKSEN_LUKU"
}

case object ValinnantuloksenLisays extends Operation {
  def name: String = "VALINNANTULOKSEN_LISAYS"
}

case object ValinnantuloksenMuokkaus extends Operation {
  def name: String = "VALINNANTULOKSEN_MUOKKAUS"
}

case object ValinnantuloksenPoisto extends Operation {
  def name: String = "VALINNANTULOKSEN_POISTO"
}

case object LukuvuosimaksujenLuku extends Operation {
  def name: String = "LUKUVUOSIMAKSUJEN_LUKU"
}

case object LukuvuosimaksujenMuokkaus extends Operation {
  def name: String = "LUKUVUOSIMAKSUJEN_MUOKKAUS"
}
case object HyvaksymiskirjeidenLuku extends Operation {
  def name: String = "HYVAKSYMISKIRJEIDEN_LUKU"
}

case object HyvaksymiskirjeidenMuokkaus extends Operation {
  def name: String = "HYVAKSYMISKIRJEIDEN_MUOKKAUS"
}

case object HyvaksymiskirjeidenPoisto extends Operation {
  def name: String = "HYVAKSYMISKIRJEIDEN_POISTO"
}

case object VastaanottotietojenLuku extends Operation {
  def name: String = "VASTAANOTTOTIETOJEN_LUKU"
}

case object ValintaesityksenHyvaksyminen extends Operation {
  def name: String = "VALINTAESITYKSEN_HYVAKSYMINEN"
}

case object ValintaesityksenLuku extends Operation {
  def name: String = "VALINTAESITYKSEN_LUKU"
}

case object SijoittelunHakukohteenLuku extends Operation {
  def name: String = "SIJOITTELUN_HAKUKOHTEEN_LUKU"
}

case object SijoittelunHakemuksenLuku extends Operation {
  def name: String = "SIJOITTELUN_HAKEMUKSEN_LUKU"
}

case object SijoitteluAjonLuku extends Operation {
  def name: String = "SIJOITTELUAJON_LUKU"
}

case object PuuttuvienTulostenLuku extends Operation {
  def name: String = "PUUTTUVIEN_TULOSTEN_LUKU"
}

case object PuuttuvienTulostenTaustapaivityksenTilanLuku extends Operation {
  def name: String = "PUUTTUVIEN_TULOSTEN_TAUSTAPAIVITYKSEN_TILAN_LUKU"
}

case object PuuttuvienTulostenTaustapaivityksenPaivitys extends Operation {
  def name: String = "PUUTTUVIEN_TULOSTEN_PAIVITYS"
}

case object PuuttuvienTulostenYhteenvedonLuku extends Operation {
  def name: String = "PUUTTUVIEN_TULOSTEN_YHTEENVEDON_LUKU"
}

case object VastaanottoPostitietojenLuku extends Operation {
  def name: String = "VASTAANOTTOSAHKOPOSTITIETOJEN_LUKU"
}

case object VastaanottoPostitietojenPoisto extends Operation {
  def name: String = "VASTAANOTTOSAHKOPOSTITIETOJEN_POISTO"
}