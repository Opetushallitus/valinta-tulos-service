package fi.vm.sade.valintatulosservice.generatedfixtures

import fi.vm.sade.sijoittelu.domain.HakemuksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

/**
 * Luo annetun määrän hakukohteita ja hakemuksia. Kaikki hakemukset hakevat kaikkiin hakukohteisiin.
 * Joka toinen on HYVÄKSYTTY, joka toinen HYLÄTTY
 *
 * @param hakukohteita
 * @param hakemuksia
 * @param hakuOid
 */
case class SimpleGeneratedHakuFixture(hakukohteita: Int, hakemuksia: Int, override val hakuOid: HakuOid = HakuOid("1"), hakemuksenTilat: List[HakemuksenTila] = Nil) extends GeneratedHakuFixture(hakuOid) {
  override val hakemukset: List[HakemuksenTulosFixture] = (1 to hakemuksia).map { hakemusNumero =>
    val hakutoiveet: List[HakemuksenHakukohdeFixture] = (1 to hakukohteita).map { hakukohdeNumero =>
      val hakukohdeOid = HakukohdeOid(hakukohdeNumero.toString)
      val tarjoajaOid = hakukohdeNumero.toString
      val totalIndex = (hakemusNumero-1) * hakukohteita + (hakukohdeNumero-1)
      val tila = if(!hakemuksenTilat.isEmpty) { hakemuksenTilat(totalIndex % hakemuksenTilat.size)
      } else if (totalIndex % 2 == 0) { HakemuksenTila.HYVAKSYTTY } else { HakemuksenTila.HYLATTY}

      HakemuksenHakukohdeFixture(tarjoajaOid, hakukohdeOid, jonot = List(ValintatapaJonoFixture(tila)))
    }.toList

    val hakemusOid = HakemusOid(hakuOid.toString + "." + hakemusNumero.toString)
    HakemuksenTulosFixture(hakemusOid, hakutoiveet)
  }.toList
}

case class SimpleGeneratedHakuFixture2(hakukohteita: Int, hakemuksia: Int, override val hakuOid: HakuOid = HakuOid("1")) extends GeneratedHakuFixture(hakuOid) {
  override val hakemukset: List[HakemuksenTulosFixture] = (1 to hakemuksia).map { hakemusNumero =>
    val hakutoiveet: List[HakemuksenHakukohdeFixture] = List({
      val hakukohdeNumero = ( hakemusNumero % hakukohteita ) + 1
      val hakukohdeOid = HakukohdeOid(hakukohdeNumero.toString)
      val tarjoajaOid = hakukohdeNumero.toString
      val totalIndex = (hakemusNumero-1) * hakukohteita + (hakukohdeNumero-1)
      val tila = if (totalIndex % 2 == 0) { HakemuksenTila.HYVAKSYTTY } else { HakemuksenTila.HYLATTY}

      HakemuksenHakukohdeFixture(tarjoajaOid, hakukohdeOid, jonot = List(ValintatapaJonoFixture(tila)))
    })

    val hakemusOid = HakemusOid(hakuOid.toString + "." + hakemusNumero.toString)
    HakemuksenTulosFixture(hakemusOid, hakutoiveet)
  }.toList
}

