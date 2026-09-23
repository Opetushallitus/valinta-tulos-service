package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ohjausparametrit.OhjausparametritService
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintarekisteriRaportointiService, ValintarekisteriSijoittelunTulosClient}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriDb
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SijoittelutulosServiceSpec extends Specification with Mockito {

  private val hakuOid = HakuOid("1.2.246.562.29.75203638285")
  private val hakukohdeOid = HakukohdeOid("1.2.246.562.20.26643418986")

  "haeVastaanotonAikarajaTiedot" in {
    "ei tee yhtaan kyselya, jos yksikaan tulos ei tarvitse takarajatietoa" in {
      val raportointiService = mock[ValintarekisteriRaportointiService]
      val ohjausparametritService = mock[OhjausparametritService]
      val valintarekisteriDb = mock[ValintarekisteriDb]
      val sijoittelunTulosClient = mock[ValintarekisteriSijoittelunTulosClient]
      // Stubattu erikseen, jotta ilman aikaista paluuta testi kaatuu selkeaan
      // "kutsuttiin sittenkin" -vaitteeseen eika NPE-tyyppiseen MatchErroriin.
      sijoittelunTulosClient.fetchLatestSijoitteluAjo(any(), any()) returns None
      val service = new SijoittelutulosService(
        raportointiService, ohjausparametritService, valintarekisteriDb, sijoittelunTulosClient)

      service.haeVastaanotonAikarajaTiedot(hakuOid, hakukohdeOid, Set.empty[HakemusOid]) must_== Set.empty

      // Ilman aikaista paluuta nama kaikki ajettaisiin ja koko hakukohde ladattaisiin,
      // vaikka lopputulos suodattuisi joka tapauksessa tyhjaksi.
      there was no(sijoittelunTulosClient).fetchLatestSijoitteluAjo(any(), any())
      there was no(valintarekisteriDb).findHyvaksyttyJulkaistuDatesForHakukohde(any())
      there was no(raportointiService).hakemuksetVainHakukohteenTietojenKanssa(any(), any())
      there was no(ohjausparametritService).ohjausparametrit(any())
    }
  }
}
