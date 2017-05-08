package fi.vm.sade.valintatulosservice.generatedfixtures

import fi.vm.sade.sijoittelu.domain.HakemuksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}

import scala.util.Random

/**
 * Luo annetun määrän hakukohteita ja hakemuksia.
 * Joka toinen on HYVÄKSYTTY, joka toinen HYLÄTTY
 *
 * @param hakukohteita
 * @param hakemuksia
 * @param hakuOid
 */
case class RandomizedGeneratedHakuFixture(hakukohteita: Int, hakemuksia: Int, kohteitaPerHakemus: Int = 5, jonojaPerKohde: Int = 2, override val hakuOid: HakuOid = HakuOid("1")) extends GeneratedHakuFixture(hakuOid) {
  val random = new Random()
  override val hakemukset: List[HakemuksenTulosFixture] = (1 to hakemuksia).map { hakemusNumero =>
    val hakutoiveet: List[HakemuksenHakukohdeFixture] = (1 to kohteitaPerHakemus).map { prio =>
      val hakukohdeNumero = random.nextInt(hakukohteita) + 1
      val hakukohdeOid = HakukohdeOid(hakukohdeNumero.toString)
      val tarjoajaOid = hakukohdeNumero.toString
      val totalIndex = (hakemusNumero-1) * hakukohteita + (hakukohdeNumero-1)
      val tila = if (prio % 2 == 0) { HakemuksenTila.HYVAKSYTTY } else { HakemuksenTila.HYLATTY}

      HakemuksenHakukohdeFixture(tarjoajaOid, hakukohdeOid, jonot = (1 to jonojaPerKohde).map{ _ => ValintatapaJonoFixture(tila)}.toList)
    }.toList

    val hakemusOid = HakemusOid(hakuOid.toString + "." + hakemusNumero.toString)
    HakemuksenTulosFixture(hakemusOid, hakutoiveet)
  }.toList
}

