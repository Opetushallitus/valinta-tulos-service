package fi.vm.sade.valintatulosservice.valintarekisteri.sijoittelu

import fi.vm.sade.valintatulosservice.ohjausparametrit.{Ohjausparametrit, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, SijoitteluRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakemusRecord, HakukohdeOid, ValintatapajonoOid}
import org.joda.time.DateTime
import org.specs2.mock.Mockito
import org.specs2.mock.Mockito.theStubbed
import org.specs2.mutable.Specification

import java.time.OffsetDateTime

class sijoitteluajonHakukohdeSpec extends Specification {

  private val hakijaOid = "3.2.1"
  private val hakukohde = HakukohdeOid("1.2.3")
  private val hakemusOid = HakemusOid("3.3.3")
  private val valintatapajonoOid = ValintatapajonoOid("5.5.5")
  private val hakemusRecord = new HakemusRecord(Some(hakijaOid), hakemusOid, None, 0,  0,
    0, null, 0, None, false,
    None, false, false, valintatapajonoOid)

  private val hyvaksyttyJaJulkaistuDates = Map(hakijaOid -> Map(hakukohde -> OffsetDateTime.now()))
  private val ohj = new Ohjausparametrit(new Vastaanottoaikataulu(Some(new DateTime()), Some(0)), None,
    None, None, None, None, None, false, false, false)
  private val repoMock = Mockito.mock[SijoitteluRepository with HakijaVastaanottoRepository]
  repoMock.getSijoitteluajonHakijaryhmat(100) returns List.empty

  "sijoitteluajonHakukohdeSpec" should {
    "getVastaanOttoDeadline returns result" in {
      new SijoitteluajonHakukohteet(repoMock, 100, None)
        .getVastaanOttoDeadline(hakemusRecord, ohj, hyvaksyttyJaJulkaistuDates, hakukohde).isDefined must beTrue
    }

    "getVastaanOttoDeadline returns None" in {
      new SijoitteluajonHakukohteet(repoMock, 100, None)
        .getVastaanOttoDeadline(hakemusRecord, ohj, hyvaksyttyJaJulkaistuDates, HakukohdeOid("7.7.7")).isDefined must beFalse
      new SijoitteluajonHakukohteet(repoMock, 100, None)
        .getVastaanOttoDeadline(hakemusRecord, ohj, Map("2.3.1" -> Map(hakukohde -> OffsetDateTime.now())), hakukohde).isDefined must beFalse
    }
  }
}
