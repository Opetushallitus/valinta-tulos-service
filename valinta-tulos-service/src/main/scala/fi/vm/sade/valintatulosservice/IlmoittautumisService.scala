package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.time.temporal.ChronoUnit

import fi.vm.sade.sijoittelu.domain.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.domain.Ilmoittautumistila.Ilmoittautumistila
import fi.vm.sade.valintatulosservice.domain.{Ilmoittautuminen, Ilmoittautumistila}
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.sijoittelu.ValintatulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakijaVastaanottoRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.VastaanotaSitovasti
import org.json4s.jackson.Serialization

class IlmoittautumisService(valintatulosService: ValintatulosService,
                            tulokset: ValintatulosRepository,
                            hakijaVastaanottoRepository: HakijaVastaanottoRepository) extends JsonFormats {
  def getIlmoittautumistilat(valintatapajonoOid: String): Seq[(String, Ilmoittautumistila, Instant)] = {
    tulokset.findValintatulokset(valintatapajonoOid).right.get
      .map(v => (
        v.getHakemusOid,
        Ilmoittautumistila.withName(v.getIlmoittautumisTila.toString),
        Option(v.getViimeinenMuutos).map(_.toInstant).getOrElse(Instant.EPOCH).truncatedTo(ChronoUnit.SECONDS))
        )
  }

  def setIlmoittautumistila(hakemusOid: String,
                            valintatapajonoOid: String,
                            tila: Ilmoittautumistila,
                            muokkaaja: String,
                            ifUnmodifiedSince: Instant): Unit = {
    tulokset.modifyValintatulos(
      valintatapajonoOid,
      hakemusOid,
      valintatulos => {
        val lastModified = Option(valintatulos.getViimeinenMuutos)
          .map(_.toInstant).getOrElse(Instant.EPOCH)
          .truncatedTo(ChronoUnit.SECONDS)
        if (lastModified.isAfter(ifUnmodifiedSince)) {
          throw new IllegalStateException("stale read")
        }
        valintatulos.setIlmoittautumisTila(
          IlmoittautumisTila.valueOf(tila.toString),
          "Ilmoittautumistilan muutos",
          muokkaaja
        )
      }
    ).left.foreach(e => throw e)
  }

  def ilmoittaudu(hakemusOid: String, ilmoittautuminen: Ilmoittautuminen) {
    val hakemuksenTulos = valintatulosService.hakemuksentulos(hakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
    val hakutoive = hakemuksenTulos.findHakutoive(ilmoittautuminen.hakukohdeOid).map(_._1).getOrElse(throw new IllegalArgumentException("Hakutoivetta ei löydy"))

    if (!hakutoive.ilmoittautumistila.ilmoittauduttavissa)  {
      throw new IllegalStateException(s"""Hakutoive ${ilmoittautuminen.hakukohdeOid} ei ole ilmoittauduttavissa: ilmoittautumisaika: ${Serialization.write(hakutoive.ilmoittautumistila.ilmoittautumisaika)}, ilmoittautumistila: ${hakutoive.ilmoittautumistila.ilmoittautumistila}, valintatila: ${hakutoive.valintatila}, vastaanottotila: ${hakutoive.vastaanottotila}""")
    }

    val vastaanotto = hakijaVastaanottoRepository.runBlocking(hakijaVastaanottoRepository.findHenkilonVastaanototHaussa(hakemuksenTulos.hakijaOid, hakemuksenTulos.hakuOid))
    if (!vastaanotto.exists(v => {
      v.action == VastaanotaSitovasti && v.hakukohdeOid == ilmoittautuminen.hakukohdeOid
    })) {
      throw new IllegalStateException(s"Hakija ${hakemuksenTulos.hakijaOid} ei voi ilmoittautua hakukohteeseen ${hakutoive.hakukohdeOid} koska sitovaa vastaanottoa ei löydy.")
    }

    tulokset.modifyValintatulos(
      ilmoittautuminen.hakukohdeOid,
      hakutoive.valintatapajonoOid,
      hakemusOid,
      valintatulos =>
        valintatulos.setIlmoittautumisTila(
          IlmoittautumisTila.valueOf(ilmoittautuminen.tila.toString),
          ilmoittautuminen.selite,
          ilmoittautuminen.muokkaaja
        )
    ).left.foreach(e => throw e)
  }
}
