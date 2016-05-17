package fi.vm.sade.valintatulosservice.valintarekisteri

import fi.vm.sade.valintatulosservice.domain.{HakukohdeRecord, Kausi}
import fi.vm.sade.valintatulosservice.koodisto.{KoodistoService, Koodi, KoodiUri, Relaatiot}
import fi.vm.sade.valintatulosservice.tarjonta._
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
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== hakukohdeRecord
      there was noMoreCallsTo(hakuService)
    }
    "invokes tarjonta when hakukohde record is not found" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)
      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Some(hakukohdeFromTarjonta)
      hakuService.getHaku(hakuOid) returns Some(hakuFromTarjonta)
      hakuService.getKoulutus(julkaistuKoulutus.oid) returns Some(julkaistuKoulutus)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== hakukohdeRecord
      one(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  "Strict HakukohdeRecordService" in {
    "throws an exception for hakukohde with conflicting koulutuksen alkamiskausi" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, false)
      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Some(hakukohdeFromTarjonta.copy(hakukohdeKoulutusOids = List(luonnosKoulutus.oid)))
      hakuService.getHaku(hakuOid) returns Some(hakuFromTarjonta)
      hakuService.getKoulutus(luonnosKoulutus.oid) returns Some(luonnosKoulutus)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must throwA[HakukohdeDetailsRetrievalException]
      there was no(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  "Lenient HakukohdeRecordService" in {
    "falls back to koulutuksen alkamiskausi of haku for hakukohde with conflicting koulutuksen alkamiskausi" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)
      val hakukohdeRecordWithKausiFromHaku: HakukohdeRecord = hakukohdeRecord.copy(koulutuksenAlkamiskausi = hakuFromTarjonta.koulutuksenAlkamiskausi.get)

      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Some(hakukohdeFromTarjonta.copy(hakukohdeKoulutusOids = List(luonnosKoulutus.oid)))
      hakuService.getHaku(hakuOid) returns Some(hakuFromTarjonta)
      hakuService.getKoulutus(luonnosKoulutus.oid) returns Some(luonnosKoulutus)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must_== hakukohdeRecordWithKausiFromHaku
      one(hakukohdeRepository).storeHakukohde(hakukohdeRecordWithKausiFromHaku)
    }

    "crashes if haku has no koulutuksen alkamiskausi for hakukohde with conflicting koulutuksen alkamiskausi" in new HakukohdeRecordServiceWithMocks {
      val hakukohdeRecordService = new HakukohdeRecordService(hakuService, hakukohdeRepository, true)

      hakukohdeRepository.findHakukohde(hakukohdeOid) returns None
      hakuService.getHakukohde(hakukohdeOid) returns Some(hakukohdeFromTarjonta.copy(hakukohdeKoulutusOids = List(luonnosKoulutus.oid)))
      hakuService.getHaku(hakuOid) returns Some(hakuFromTarjonta.copy(koulutuksenAlkamiskausi = None))
      hakuService.getKoulutus(luonnosKoulutus.oid) returns Some(luonnosKoulutus)
      hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid) must throwA[HakukohdeDetailsRetrievalException]
      there was no(hakukohdeRepository).storeHakukohde(hakukohdeRecord)
    }
  }

  trait HakukohdeRecordServiceWithMocks extends Mockito with Scope with MustThrownExpectations {
    val hakuService = mock[HakuService]
    val hakukohdeRepository = mock[HakukohdeRepository]
    val hakuOid = "1.2.246.562.5.73892938273982732"
    val hakukohdeOid = "1.2.246.562.5.4890340398"
    val hakukohdeRecord = HakukohdeRecord(hakukohdeOid, hakuOid = hakuOid,
      yhdenPaikanSaantoVoimassa = true, kktutkintoonJohtava = true, Kausi("2016S"))

    val koodi = Koodi(KoodiUri("koulutus_000000"), 1, Some(Relaatiot(Nil, Nil, List(Koodi(KoodistoService.OnTutkinto, 1, None)))))
    val luonnosKoulutus = Koulutus("1.2.246.562.17.53513994722", Kausi("2017S"), "LUONNOS", Some(koodi))
    val julkaistuKoulutus = Koulutus("1.2.246.562.17.42423443434", Kausi("2016S"), "JULKAISTU", Some(koodi))

    val hakukohdeFromTarjonta = Hakukohde(hakukohdeOid, hakuOid, List(julkaistuKoulutus.oid), "KORKEAKOULUTUS", "TUTKINTO",
      Map("kieli_fi" -> "Hakukohteen nimi"), Map("fi" -> "Tarjoajan nimi"))
    val hakuFromTarjonta: Haku = Haku(hakuOid, korkeakoulu = true, yhteishaku = true, varsinainenhaku = true,
      lisähaku = false, käyttääSijoittelua = true, varsinaisenHaunOid = None, sisältyvätHaut = Set(), hakuAjat = Nil,
      Some(Kausi("2016K")), YhdenPaikanSaanto(voimassa = true, "Korkeakoulutus ilman kohdejoukon tarkennetta"),
      Map("kieli_fi" -> "Haun nimi"))
  }
}
