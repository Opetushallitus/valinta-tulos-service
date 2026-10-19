package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakukohdeOid, Ilmoittautuminen, SijoitteluajonIlmoittautumistila, VastaanotaSitovasti}
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.util.Try

class IlmoittautumisService(valintatulosService: ValintatulosService, hakijaVastaanottoRepository: HakijaVastaanottoRepository, valinnantulosRepository: ValinnantulosRepository) extends JsonFormats {
  private val logger = LoggerFactory.getLogger(classOf[IlmoittautumisService])

  def ilmoittaudu(hakemusOid: HakemusOid, ilmoittautuminen: Ilmoittautuminen): Unit = {
    tallennaIlmoittautuminen(haeHakemuksenTulos(hakemusOid), ilmoittautuminen)
  }

  /**
   * Hakijan omasta puolestaan tekemä ilmoittautuminen. Muokkaajaa ei anneta rajapintaan, vaan se
   * päätellään hakemukselta, jolloin ilmoittautumisen ilmoittaja ja henkilo ovat aina
   * vertailukelpoiset. Kutsuja ei siis voi antaa muokkaajaksi esimerkiksi henkilön master-oidia
   * silloin, kun hakemukseen liittyy jokin sen duplikaateista.
   */
  def ilmoittauduHakijana(hakemusOid: HakemusOid,
                          hakukohdeOid: HakukohdeOid,
                          tila: SijoitteluajonIlmoittautumistila,
                          selite: String): Unit = {
    val hakemuksenTulos = haeHakemuksenTulos(hakemusOid)
    tallennaIlmoittautuminen(hakemuksenTulos, Ilmoittautuminen(hakukohdeOid, tila, hakemuksenTulos.hakijaOid, selite))
  }

  private def haeHakemuksenTulos(hakemusOid: HakemusOid): Hakemuksentulos =
    valintatulosService.hakemuksentulos(hakemusOid).getOrElse(throw new IllegalArgumentException("Hakemusta ei löydy"))

  private def tallennaIlmoittautuminen(hakemuksenTulos: Hakemuksentulos, ilmoittautuminen: Ilmoittautuminen): Unit = {
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

    Try(valinnantulosRepository.runBlocking(valinnantulosRepository.storeIlmoittautuminen(hakemuksenTulos.hakijaOid, ilmoittautuminen))).recover {
      case e =>
        logger.error(s"Hakijan ${hakemuksenTulos.hakijaOid} ilmoittautumista ei saatu SQL-kantaan!",e)
    }

  }
}
