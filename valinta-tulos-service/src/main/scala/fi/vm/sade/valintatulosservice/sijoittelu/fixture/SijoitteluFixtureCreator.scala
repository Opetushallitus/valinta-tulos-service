package fi.vm.sade.valintatulosservice.sijoittelu.fixture

import java.util
import java.util.Date

import fi.vm.sade.sijoittelu.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid, ValintatapajonoOid}

object SijoitteluFixtureCreator {
  import scala.collection.JavaConverters._


  def newHakemus(hakemusOid: HakemusOid, hakijaOid: String, hakutoiveIndex: Int, hakemuksenTila: HakemuksenTila): Hakemus = {
    val hakemus = new Hakemus
    hakemus.setHakijaOid(hakijaOid)
    hakemus.setHakemusOid(hakemusOid.toString)
    hakemus.setPrioriteetti(hakutoiveIndex)
    hakemus.setJonosija(1)
    hakemus.setPisteet(new java.math.BigDecimal(4))
    hakemus.setTasasijaJonosija(1)
    hakemus.setTila(hakemuksenTila)
    val historia: TilaHistoria = new TilaHistoria()
    historia.setLuotu(new Date())
    historia.setTila(hakemuksenTila)
    hakemus.getTilaHistoria.add(historia)
    hakemus
  }

  def newValintatapajono(jonoOid: ValintatapajonoOid, hakemukset: List[Hakemus]) = {
    val jono = new Valintatapajono()
    jono.setTasasijasaanto(Tasasijasaanto.YLITAYTTO)
    jono.setOid(jonoOid.toString)
    jono.setNimi("testijono")
    jono.setPrioriteetti(0)
    jono.setAloituspaikat(3)
    jono.setHakemukset(new util.ArrayList(hakemukset.asJava))
    jono
  }

  def newHakukohde(hakukohdeOid: HakukohdeOid, tarjoajaOid: String, sijoitteluajoId: Long, kaikkiJonotSijoiteltu: Boolean, jonot: List[Valintatapajono]) = {
    val hakukohde = new Hakukohde()
    hakukohde.setSijoitteluajoId(sijoitteluajoId)
    hakukohde.setOid(hakukohdeOid.toString)
    hakukohde.setTarjoajaOid(tarjoajaOid)
    hakukohde.setKaikkiJonotSijoiteltu(kaikkiJonotSijoiteltu)
    hakukohde.setValintatapajonot(jonot.asJava)
    hakukohde
  }

  def newValintatulos(jonoOid: ValintatapajonoOid, hakuOid: HakuOid, hakemusOid: HakemusOid, hakukohdeOid: HakukohdeOid, hakijaOid: String, hakutoiveIndex: Int, julkaistavissa: Boolean = true) = {
    val valintatulos = new Valintatulos(
      jonoOid.toString,
      hakemusOid.toString,
      hakukohdeOid.toString,
      hakijaOid,
      hakuOid.toString,
      hakutoiveIndex
    )
    valintatulos.setJulkaistavissa(julkaistavissa, "testing", hakijaOid)
    valintatulos
  }

  def newSijoittelu(hakuOid: HakuOid, sijoitteluajoId: Long, hakukohdeOids: List[HakukohdeOid]): Sijoittelu = {
    val sijoitteluAjo = new SijoitteluAjo
    sijoitteluAjo.setSijoitteluajoId(sijoitteluajoId)
    sijoitteluAjo.setHakuOid(hakuOid.toString)
    sijoitteluAjo.setStartMils(System.currentTimeMillis())
    sijoitteluAjo.setEndMils(System.currentTimeMillis())
    sijoitteluAjo.setHakukohteet(hakukohdeOids.map { hakukohdeOid =>
      val item = new HakukohdeItem()
      item.setOid(hakukohdeOid.toString)
      item
    }.asJava)

    val sijoittelu = new Sijoittelu()
    sijoittelu.setHakuOid(hakuOid.toString)
    sijoittelu.setSijoitteluId(1l)
    sijoittelu.setCreated(new Date)
    sijoittelu.setSijoittele(true)
    sijoittelu.getSijoitteluajot.add(sijoitteluAjo)
    sijoittelu
  }
}
