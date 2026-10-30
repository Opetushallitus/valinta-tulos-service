package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.domain.Hakemuksentulos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

/**
 * Rajaa yksittäisen hakemuksen tuloksen käyttäjän organisaatioiden hakukohteisiin.
 *
 * Hakutoiveet, joiden hakukohteeseen käyttäjällä ei ole oikeutta, poistetaan. Hakutoivekohtaiset tilat
 * (esim. vastaanotettavuus ja kesken-päättely) on laskettu koko hakemuksen perusteella ennen rajausta, joten ne
 * pysyvät oikeina, vaikka ylempi hakutoive ei näkyisi kutsujalle. Jos hakutoiveita poistetaan eikä yhtään jää
 * jäljelle, palautetaan None, jotta hakemuksen olemassaoloa haussa ei paljasteta. Jos mitään ei poisteta, tulos
 * palautetaan sellaisenaan, myös hakutoiveettomalle hakemukselle.
 *
 * Tekee verkkokutsuja vain salliHakukohde-funktion kautta.
 */
object HakemuksenTuloksenOrganisaatiorajaus {

  def rajaa(tulos: Hakemuksentulos, salliHakukohde: HakukohdeOid => Boolean): Option[Hakemuksentulos] = {
    val sallitut = tulos.hakutoiveet.filter(toive => salliHakukohde(toive.hakukohdeOid))
    if (sallitut.size == tulos.hakutoiveet.size) Some(tulos)
    else if (sallitut.isEmpty) None
    else Some(tulos.copy(hakutoiveet = sallitut))
  }
}
