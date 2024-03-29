package fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde

import fi.vm.sade.valintatulosservice.tarjonta._
import fi.vm.sade.valintatulosservice.valintarekisteri.db.HakukohdeRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakuOid, HakukohdeOid, HakukohdeRecord, Kausi, YPSHakukohde}
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{CalledMatchers, MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class HakukohdeRecordServiceSpec extends Specification with MockitoMatchers with MockitoStubs with CalledMatchers {

  "HakukohdeRecordService" in {
    "returns hakukohde records directly from db when found" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)

      hakukohdeRepository.findHakukohde(hakukohdeOid) returns Some(hakukohdeRecord)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== Right(hakukohdeRecord)
      there was noMoreCallsTo(hakuService)
    }
    "invokes tarjonta when hakukohde record is not found" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)
      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Right(hakukohdeFromTarjonta)
      hakuService.getHaku(hakuOid) returns Right(hakuFromTarjonta)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== Right(hakukohdeRecord)
      one(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  "Strict HakukohdeRecordService" in {
    "throws an exception when neither haku has koulutuksen alkamiskausi" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, false)
      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Right(hakukohdeFromTarjonta.copy(koulutuksenAlkamiskausiUri = Some("")))
      hakuService.getHaku(hakuOid) returns Right(hakuFromTarjonta)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must beLeft[Throwable]
      there was no(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  "Lenient HakukohdeRecordService" in {
    "falls back to koulutuksen alkamiskausi of haku for hakukohde when hakukohde has no koulutuksen alkamiskausi" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)
      val hakukohdeRecordWithKausiFromHaku: HakukohdeRecord = hakukohdeRecord.copy(koulutuksenAlkamiskausi = hakuFromTarjonta.koulutuksenAlkamiskausi.get)

      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Right(hakukohdeFromTarjonta.copy(koulutuksenAlkamiskausiUri = Some("")))
      hakuService.getHaku(hakuOid) returns Right(hakuFromTarjonta)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== Right(hakukohdeRecordWithKausiFromHaku)
      one(hakukohdeRepository).storeHakukohde(hakukohdeRecordWithKausiFromHaku)
    }

    "crashes if haku has no koulutuksen alkamiskausi for hakukohde or haku" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)

      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Right(hakukohdeFromTarjonta.copy(koulutuksenAlkamiskausiUri = Some("")))
      hakuService.getHaku(hakuOid) returns Right(hakuFromTarjonta.copy(koulutuksenAlkamiskausi = None))
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must beLeft[Throwable]
      there was no(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  trait HakukohdeRecordServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    val hakuService = mock[HakuService]
    val hakukohdeRepository = mock[HakukohdeRepository]
    val hakuOid = HakuOid("1.2.246.562.5.73892938273982732")
    val hakukohdeOid = HakukohdeOid("1.2.246.562.5.4890340398")
    val hakukohdeRecord = YPSHakukohde(hakukohdeOid, hakuOid, Kausi("2016K"))

    val yhdenpaikansaanto = YhdenPaikanSaanto(voimassa = true, "Korkeakoulutus ilman kohdejoukon tarkennetta")
    val hakukohdeFromTarjonta = Hakukohde(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      tarjoajaOids = Set("123.123.123.123"),
      koulutusAsteTyyppi = "KORKEAKOULUTUS",
      hakukohteenNimet = Map("kieli_fi" -> "Hakukohteen nimi"),
      tarjoajaNimet = Map("fi" -> "Tarjoajan nimi"),
      yhdenPaikanSaanto = yhdenpaikansaanto,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2016),
      organisaatioRyhmaOids = Set())

    val hakuFromTarjonta: Haku = Haku(
      hakuOid,
      yhteishaku = true,
      korkeakoulu = true,
      toinenAste = false,
      sallittuKohdejoukkoKelaLinkille = true,
      käyttääSijoittelua = true,
      käyttääHakutoiveidenPriorisointia = true,
      varsinaisenHaunOid = None,
      sisältyvätHaut = Set(),
      koulutuksenAlkamiskausi = Some(Kausi("2016K")),
      yhdenPaikanSaanto = yhdenpaikansaanto,
      nimi = Map("kieli_fi" -> "Haun nimi"))
  }
}
