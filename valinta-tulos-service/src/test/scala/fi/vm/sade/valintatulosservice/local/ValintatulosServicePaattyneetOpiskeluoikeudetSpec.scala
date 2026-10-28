package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.valintatulosservice.ValintatulosService
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.domain.{Hakemuksentulos, Hakutoiveentulos}
import fi.vm.sade.valintatulosservice.hakemus.HakemusRepository
import fi.vm.sade.valintatulosservice.koodisto.KoodistoService
import fi.vm.sade.valintatulosservice.ohjausparametrit.{OhjausparametritService, Vastaanottoaikataulu}
import fi.vm.sade.valintatulosservice.sijoittelu.{SijoittelutulosService, ValintarekisteriValintatulosDao}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository, VirkailijaVastaanottoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid, HakukohdeOid}
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope
import slick.dbio.DBIO

import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class ValintatulosServicePaattyneetOpiskeluoikeudetSpec extends Specification {

  "haePaattyneetOpiskeluoikeudet" should {
    "hakee päätettävät opiskeluoikeudet kannasta vain YOS-piiriin kuuluville hakukohteille" in new Mocks {
      hakuService.getHakukohde(yosHakukohdeOid) returns Right(hakukohde(yosHakukohdeOid, kuuluuYosinPiiriin = true))
      hakuService.getHakukohde(eiYosHakukohdeOid) returns Right(hakukohde(eiYosHakukohdeOid, kuuluuYosinPiiriin = false))

      val tulos = service.haePaattyneetOpiskeluoikeudet(hakemuksentulos(List(eiYosToive, yosToive)))

      tulos must_== List((eiYosToive, None), (yosToive, Some(oikeudetJson)))
      there was one(hakijaVastaanottoRepository).findHakemuksenVastaanotonPaatettavatOpiskeluOikeudet(hakemusOid, yosHakukohdeOid)
      there was no(hakijaVastaanottoRepository).findHakemuksenVastaanotonPaatettavatOpiskeluOikeudet(hakemusOid, eiYosHakukohdeOid)
    }

    "hakee päätettävät opiskeluoikeudet kannasta, jos hakukohteen haku epäonnistuu" in new Mocks {
      hakuService.getHakukohde(yosHakukohdeOid) returns Left(new RuntimeException("kouta ei vastaa"))

      val tulos = service.haePaattyneetOpiskeluoikeudet(hakemuksentulos(List(yosToive)))

      tulos must_== List((yosToive, Some(oikeudetJson)))
      there was one(hakijaVastaanottoRepository).findHakemuksenVastaanotonPaatettavatOpiskeluOikeudet(hakemusOid, yosHakukohdeOid)
    }
  }

  trait Mocks extends Mockito with Scope with MustThrownExpectations {
    implicit val appConfig: VtsAppConfig = mock[VtsAppConfig]
    val hakuService: HakuService = mock[HakuService]
    val hakijaVastaanottoRepository: HakijaVastaanottoRepository = mock[HakijaVastaanottoRepository]

    val service = new ValintatulosService(
      mock[ValinnantulosRepository],
      mock[SijoittelutulosService],
      mock[OhjausparametritService],
      mock[HakemusRepository],
      mock[VirkailijaVastaanottoRepository],
      hakuService,
      hakijaVastaanottoRepository,
      mock[HakukohdeRecordService],
      mock[ValintarekisteriValintatulosDao],
      mock[KoodistoService]
    )

    val hakuOid = HakuOid("1.2.246.562.29.00000000000000000001")
    val hakemusOid = HakemusOid("1.2.246.562.11.00000000000000000001")
    val yosHakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000000000000001")
    val eiYosHakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000000000000002")
    val oikeudetJson = """[{"virtaOpiskeluOikeusId": "1"}]"""

    val yosToive: Hakutoiveentulos = hakutoive(yosHakukohdeOid)
    val eiYosToive: Hakutoiveentulos = hakutoive(eiYosHakukohdeOid)

    val oikeudetAction: DBIO[Option[String]] = DBIO.successful(Some(oikeudetJson))
    hakijaVastaanottoRepository.findHakemuksenVastaanotonPaatettavatOpiskeluOikeudet(hakemusOid, yosHakukohdeOid) returns oikeudetAction
    hakijaVastaanottoRepository.runBlocking(===(oikeudetAction), any[Duration]) returns Some(oikeudetJson)

    def hakutoive(hakukohdeOid: HakukohdeOid): Hakutoiveentulos = {
      val toive = mock[Hakutoiveentulos]
      toive.hakukohdeOid returns hakukohdeOid
      toive
    }

    def hakemuksentulos(hakutoiveet: List[Hakutoiveentulos]): Hakemuksentulos =
      Hakemuksentulos(hakuOid, hakemusOid, "1.2.246.562.24.00000000000000000001", Vastaanottoaikataulu(None, None), hakutoiveet)

    def hakukohde(oid: HakukohdeOid, kuuluuYosinPiiriin: Boolean): Hakukohde =
      Hakukohde(
        oid = oid,
        hakuOid = hakuOid,
        tarjoajaOids = Set.empty,
        koulutusAsteTyyppi = "KORKEAKOULUTUS",
        hakukohteenNimet = Map.empty,
        tarjoajaNimet = Map.empty,
        yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = true, syy = ""),
        tutkintoonJohtava = true,
        koulutuksenAlkamiskausiUri = None,
        koulutuksenAlkamisvuosi = None,
        organisaatioRyhmaOids = Set.empty,
        kuuluuYosinPiiriin = kuuluuYosinPiiriin
      )
  }
}
