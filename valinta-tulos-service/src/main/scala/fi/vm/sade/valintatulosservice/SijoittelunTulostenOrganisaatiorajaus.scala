package fi.vm.sade.valintatulosservice

import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakijaDTO, HakijaPaginationObject, HakutoiveDTO}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

import java.util
import scala.collection.JavaConverters._

/**
 * Rajaa sijoittelun tuloslistauksen käyttäjän organisaatioiden hakukohteisiin.
 *
 * Hakijalta poistetaan hakutoiveet, joiden hakukohteeseen käyttäjällä ei ole oikeutta. Jos listaus on pyydetty
 * vain hyväksytyistä, hakija pudotetaan kokonaan, ellei hänellä ole hyväksyntää jäljelle jäävissä hakutoiveissa.
 * Hakija pudotetaan myös, jos yhtään hakutoivetta ei jää jäljelle.
 *
 * Huom: säilytettävien hakijoiden hakutoivejoukko päivitetään suoraan HakijaDTO-olioihin, koska ne ovat pyyntökohtaisia.
 * Tekee verkkokutsuja vain salliHakukohde-funktion kautta.
 */
object SijoittelunTulostenOrganisaatiorajaus {

  def rajaa(tulokset: HakijaPaginationObject,
            salliHakukohde: HakukohdeOid => Boolean,
            vainHyvaksytyt: Boolean): HakijaPaginationObject = {
    val hakijat = Option(tulokset.getResults).map(_.asScala.toList).getOrElse(Nil)
    val rajatut = hakijat.flatMap(rajaaHakija(_, salliHakukohde, vainHyvaksytyt))
    val rajattu = new HakijaPaginationObject
    rajattu.setResults(new util.ArrayList[HakijaDTO](rajatut.asJava))
    rajattu.setTotalCount(rajatut.size)
    rajattu
  }

  private def rajaaHakija(hakija: HakijaDTO, salliHakukohde: HakukohdeOid => Boolean, vainHyvaksytyt: Boolean): Option[HakijaDTO] = {
    val hakutoiveet = Option(hakija.getHakutoiveet).map(_.asScala.toList).getOrElse(Nil)
    val sallitut = hakutoiveet.filter(toive => salliHakukohde(HakukohdeOid(toive.getHakukohdeOid)))
    if (sallitut.isEmpty || (vainHyvaksytyt && !sallitut.exists(onHyvaksytty))) {
      None
    } else {
      val sorted = new util.TreeSet[HakutoiveDTO]()
      sorted.addAll(sallitut.asJava)
      hakija.setHakutoiveet(sorted)
      Some(hakija)
    }
  }

  // Sama tulkinta kuin ValintarekisteriRaportointiService.konvertoiHakijat-metodin hyvaksytyt-suodattimessa
  private def onHyvaksytty(hakutoive: HakutoiveDTO): Boolean =
    Option(hakutoive.getHakutoiveenValintatapajonot).map(_.asScala).getOrElse(Nil).exists(jono =>
      jono.getTila == HakemuksenTila.HYVAKSYTTY || jono.getTila == HakemuksenTila.VARASIJALTA_HYVAKSYTTY)
}
