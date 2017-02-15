package fi.vm.sade.valintatulosservice.local

import java.util.Date

import fi.vm.sade.valintatulosservice.VastaanotettavuusService
import fi.vm.sade.valintatulosservice.tarjonta.{Haku, Hakukohde, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, VastaanottoRecord}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.junit.runner.RunWith
import org.mockito.Matchers
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import slick.dbio._

import scala.util.{Failure, Success, Try}

@RunWith(classOf[JUnitRunner])
class VastaanotettavuusServiceSpec extends Specification with MockitoMatchers with MockitoStubs {
  "VastaanotettavuusService" in {
    "tarkistaAiemmatVastaanotot" in {
      "kun haussa yhden paikan sääntö voimassa" in {
        "kun hakijalla useita aiempia vastaanottoja" in new VastaanotettavuusServiceWithMocks with YhdenPaikanSaantoVoimassa {
          hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, kausi) returns DBIOAction.failed(new RuntimeException("test msg"))
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beFailedTry.withThrowable[RuntimeException]
        }

        "kun hakijalla yksi aiempi vastaanotto" in new VastaanotettavuusServiceWithMocks with YhdenPaikanSaantoVoimassa {
          hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, kausi) returns DBIOAction.successful(Some(previousVastaanottoRecord))
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beFailedTry.withThrowable[PriorAcceptanceException]
        }
        "kun hakijalla ei aiempia vastaanottoja" in new VastaanotettavuusServiceWithMocks with YhdenPaikanSaantoVoimassa {
          hakijaVastaanottoRepository.findYhdenPaikanSaannonPiirissaOlevatVastaanotot(henkiloOid, kausi) returns DBIOAction.successful(None)
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beSuccessfulTry
        }
      }

      "kun yhden paikan sääntö ei voimassa" in {
        "kun hakijalla useita aiempia vastaanottoja" in new VastaanotettavuusServiceWithMocks with IlmanYhdenPaikanSaantoa {
          hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohde.oid) returns DBIOAction.failed(new RuntimeException("test msg"))
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beFailedTry.withThrowable[RuntimeException]
          there was no(hakijaVastaanottoRepository).findYhdenPaikanSaannonPiirissaOlevatVastaanotot(Matchers.any[String], Matchers.any[Kausi])
        }
        "kun hakijalla yksi aiempi vastaanotto" in new VastaanotettavuusServiceWithMocks with IlmanYhdenPaikanSaantoa {
          hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohde.oid) returns DBIOAction.successful(Some(previousVastaanottoRecord))
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beFailedTry.withThrowable[PriorAcceptanceException]
          there was no(hakijaVastaanottoRepository).findYhdenPaikanSaannonPiirissaOlevatVastaanotot(Matchers.any[String], Matchers.any[Kausi])
        }
        "kun hakijalla ei aiempia vastaanottoja" in new VastaanotettavuusServiceWithMocks with IlmanYhdenPaikanSaantoa {
          hakijaVastaanottoRepository.findHenkilonVastaanottoHakukohteeseen(henkiloOid, hakukohde.oid) returns DBIOAction.successful(None)
          dbioToTry(v.tarkistaAiemmatVastaanotot(henkiloOid, hakukohde.oid)) must beSuccessfulTry
          there was no(hakijaVastaanottoRepository).findYhdenPaikanSaannonPiirissaOlevatVastaanotot(Matchers.any[String], Matchers.any[Kausi])
        }
      }
    }
  }

  trait YhdenPaikanSaantoVoimassa extends Mockito with Scope with MustThrownExpectations { this: VastaanotettavuusServiceWithMocks =>
    val henkiloOid = "1.2.246.562.24.00000000000"
    val haku = Haku("1.2.246.562.29.00000000000", korkeakoulu = true, käyttääSijoittelua = true, None, Set(), List(),
      Some(Kausi("2016S")), YhdenPaikanSaanto(true, "kk haku ilman kohdejoukon tarkennetta"), Map("kieli_fi" -> "Haun nimi"))
    val koulutusOid = "1.2.246.562.17.00000000000"
    val hakukohde = Hakukohde("1.2.246.562.20.00000000000", haku.oid, List(koulutusOid), "KORKEAKOULUTUS", "TUTKINTO",
      Map("kieli_fi" -> "Hakukohteen nimi"), Map("fi" -> "Tarjoajan nimi"), yhdenPaikanSaanto = haku.yhdenPaikanSaanto, true, "S", 2016,
      Set("123.123.123.123"))
    val kausi = Syksy(2015)
    val previousVastaanottoRecord = VastaanottoRecord(
      henkiloOid,
      haku.oid,
      hakukohde.oid,
      VastaanotaSitovasti,
      ilmoittaja = "",
      new Date(0)
    )
    hakukohdeRecordService.getHakukohdeRecord(hakukohde.oid) returns Right(HakukohdeRecord(hakukohde.oid, haku.oid, true, true, kausi))
  }

  trait IlmanYhdenPaikanSaantoa extends Mockito with Scope with MustThrownExpectations { this: VastaanotettavuusServiceWithMocks =>
    val henkiloOid = "1.2.246.562.24.00000000000"
    val hakemusOid = "1.2.246.562.99.00000000000"
    val haku = Haku("1.2.246.562.29.00000000001", true, true, None, Set(), List(),
      Some(Kausi("2016S")), YhdenPaikanSaanto(false, "ei kk haku"), Map("kieli_fi" -> "Haun nimi"))
    val koulutusOid = "1.2.246.562.17.00000000001"
    val hakukohde = Hakukohde("1.2.246.562.20.00000000001", haku.oid, List(koulutusOid), "AMMATILLINEN_PERUSKOULUTUS",
      "TUTKINTO_OHJELMA", Map("kieli_fi" -> "Hakukohteen nimi"), Map("fi" -> "Tarjoajan nimi"), yhdenPaikanSaanto = haku.yhdenPaikanSaanto, false, "S", 2016,
        Set("123.123.123.123"))
    val kausi = Syksy(2015)
    val previousVastaanottoRecord = VastaanottoRecord(
      henkiloOid,
      haku.oid,
      hakukohde.oid,
      VastaanotaSitovasti,
      ilmoittaja = "",
      new Date(0)
    )
    hakukohdeRecordService.getHakukohdeRecord(hakukohde.oid) returns Right(HakukohdeRecord(hakukohde.oid, haku.oid, false, true, kausi))
  }

  trait VastaanotettavuusServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    val hakukohdeRecordService = mock[HakukohdeRecordService]
    val hakijaVastaanottoRepository = mock[HakijaVastaanottoRepository]
    val v = new VastaanotettavuusService(hakukohdeRecordService, hakijaVastaanottoRepository)
  }

  private def dbioToTry[T](dbio: DBIO[T]): Try[Any] = dbio match {
    case FailureAction(t) => Failure(t)
    case FlatMapAction(a: SuccessAction[_], f, _) => a match {
      case SuccessAction(None) => Success()
      case SuccessAction(v: Some[_]) => dbioToTry(f(v))
      case unknown => throw new IllegalArgumentException(s"Don't know how to handle $unknown")
    }
    case FlatMapAction(a: FailureAction, _, _) => dbioToTry(a)
    case a => throw new IllegalArgumentException(s"Don't know how to handle $a")
  }
}
