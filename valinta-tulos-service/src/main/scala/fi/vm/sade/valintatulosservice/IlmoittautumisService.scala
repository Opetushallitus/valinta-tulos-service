package fi.vm.sade.valintatulosservice

import java.time.Instant
import java.time.temporal.ChronoUnit

import fi.vm.sade.sijoittelu.domain.IlmoittautumisTila
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.sijoittelu.ValintatulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{ValinnantulosRepository, HakijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Ilmoittautuminen, SijoitteluajonIlmoittautumistila, VastaanotaSitovasti}
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.util.Try

class IlmoittautumisService(valintatulosService: ValintatulosService,
                            tulokset: ValintatulosRepository,
                            hakijaVastaanottoRepository: HakijaVastaanottoRepository, valinnantulosRepository: ValinnantulosRepository) extends JsonFormats {
  private val logger = LoggerFactory.getLogger(classOf[IlmoittautumisService])
  def getIlmoittautumistilat(valintatapajonoOid: String): Either[Throwable, Seq[(String, SijoitteluajonIlmoittautumistila, Instant)]] = {
    tulokset.findValintatulokset(valintatapajonoOid).right.map(_.map(v => (
      v.getHakemusOid,
      SijoitteluajonIlmoittautumistila(v.getIlmoittautumisTila),
      Option(v.getViimeinenMuutos).map(_.toInstant).getOrElse(Instant.EPOCH).truncatedTo(ChronoUnit.SECONDS))
    ))
  }

  def getIlmoittautumistila(hakemusOid: String, valintatapajonoOid: String): Either[Throwable, (SijoitteluajonIlmoittautumistila, Instant)] = {
    tulokset.findValintatulos(valintatapajonoOid, hakemusOid).right.map(v =>
      (SijoitteluajonIlmoittautumistila(v.getIlmoittautumisTila), Option(v.getViimeinenMuutos).map(_.toInstant).getOrElse(Instant.EPOCH))
    )
  }

  def ilmoittaudu(hakemusOid: String, ilmoittautuminen: Ilmoittautuminen) {
    val hakemuksenTulos = valintatulosService.hakemuksentulos(hakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))
    val hakutoive = hakemuksenTulos.findHakutoive(ilmoittautuminen.hakukohdeOid).map(_._1).getOrElse(throw new IllegalArgumentException("Hakutoivetta ei löydy"))

    if (!hakutoive.ilmoittautumistila.ilmoittauduttavissa)  {
      throw new IllegalStateException(s"Hakutoive ${ilmoittautuminen.hakukohdeOid} ei ole ilmoittauduttavissa: " +
        s"ilmoittautumisaika: ${Serialization.write(hakutoive.ilmoittautumistila.ilmoittautumisaika)}, " +
        s"ilmoittautumistila: ${hakutoive.ilmoittautumistila.ilmoittautumistila.ilmoittautumistila}, " +
        s"valintatila: ${hakutoive.valintatila}, " +
        s"vastaanottotila: ${hakutoive.vastaanottotila}")
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
          ilmoittautuminen.tila.ilmoittautumistila,
          ilmoittautuminen.selite,
          ilmoittautuminen.muokkaaja
        )
    ).left.foreach(e => throw e)
    Try(valinnantulosRepository.storeIlmoittautuminen(hakemuksenTulos.hakijaOid, ilmoittautuminen)).recover {
      case e =>
        logger.error(s"Hakijan ${hakemuksenTulos.hakijaOid} ilmoittautumista ei saatu SQL-kantaan!",e)
    }

  }
}
