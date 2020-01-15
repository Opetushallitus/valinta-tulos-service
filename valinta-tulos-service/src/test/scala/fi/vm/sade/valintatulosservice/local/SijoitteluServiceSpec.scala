package fi.vm.sade.valintatulosservice.local

import java.net.InetAddress
import java.time.Instant
import java.util.{Date, UUID}

import fi.vm.sade.auditlog._
import fi.vm.sade.security.OrganizationHierarchyAuthorizer
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.json.JsonFormats
import fi.vm.sade.valintatulosservice._
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket, Session}
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaRepository, SijoitteluRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mock.mockito.{MockitoMatchers, MockitoStubs}
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class SijoitteluServiceSpec extends Specification with MockitoMatchers with MockitoStubs {

  val sijoitteluajoId = 123456l
  val hakuOid = HakuOid("1.2.3")
  val hakemusOid = HakemusOid("1.2.3.4")
  val hakukohdeOid = HakukohdeOid("1.2.3.4.5")
  val tarjoajaOid = "1.2.3.4.5.6.7"
  val organisaatioRyhmaOid = "1.2.246.562.28.29795861441"
  val session = CasSession(ServiceTicket("myFakeTicket"), "1.2.246.562.24.1", Set(Role.SIJOITTELU_CRUD))
  val sessionId = UUID.randomUUID()
  val auditInfo = AuditInfo((sessionId, session), InetAddress.getLocalHost, "user-agent")
  trait Authorized { this: SijoitteluServiceMocks =>
    authorizer.checkAccess(any[Session], any[Set[String]], any[Set[Role]]) returns Right(())
  }

  "SijoitteluService" in {
    "return correct sijoittelun tulos for hakukohde" in new SijoitteluServiceMocks {
      val hakukohde = service.getHakukohdeBySijoitteluajo(hakuOid, "latest", hakukohdeOid, session, auditInfo)

      there was one (sijoitteluRepository).getLatestSijoitteluajoId("latest", hakuOid)
      there was one (sijoitteluRepository).getSijoitteluajonHakukohde(sijoitteluajoId, hakukohdeOid)
      there was one (sijoitteluRepository).getHakukohteenValintatapajonot(sijoitteluajoId, hakukohdeOid)
      there was one (sijoitteluRepository).getHakukohteenHakemukset(sijoitteluajoId, hakukohdeOid)
      there was one (sijoitteluRepository).getHakukohteenTilahistoriat(sijoitteluajoId, hakukohdeOid)
      there was one (sijoitteluRepository).getHakukohteenHakijaryhmat(sijoitteluajoId, hakukohdeOid)
      there was one (sijoitteluRepository).getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId, List("hakijaryhma1"))
      there was one (sijoitteluRepository).getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId, List("hakijaryhma1"))
      there was one (sijoitteluRepository).getValinnantilanKuvaukset(List(123))

      JsonFormats.javaObjectToJsonString(hakukohde) mustEqual JsonFormats.javaObjectToJsonString(createExpected)
    }
    "auditlogittaa sijoittelutietojen luvun" in new SijoitteluServiceMocks with Authorized {
      val hakukohde = service.getHakukohdeBySijoitteluajo(hakuOid, "latest", hakukohdeOid, session, auditInfo)
      there was one(audit).log(any[User], argThat[Operation, Operation](be_==(SijoittelunHakukohteenLuku)), any[Target], any[Changes])
    }
  }

  import java.time.temporal.ChronoUnit
  val now = Instant.now()

  def createExpected = {
    SijoittelunHakukohdeRecord(sijoitteluajoId, hakukohdeOid, true).dto(
      List(
        ValintatapajonoRecord("arvonta", ValintatapajonoOid("valintatapajono1"), "valintatapajono1", 1, Some(10), Some(10), 1, true, true, true, None, 0, None, None, None, None, None, false, hakukohdeOid).dto(List(
          Some(HakemusRecord(Some("123.1"), HakemusOid("1234.1"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono1"))).map(h => h.dto(
            Set("hakijaryhma1"),
            Some(TilankuvausRecord(123, EiTilankuvauksenTarkennetta, Some("textFi"), Some("textSv"), Some("textEn"))),
            List(TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.1"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)).dto,
                 TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.1"), Hylatty, new Date(now.minus(4, ChronoUnit.DAYS).toEpochMilli)).dto)
            )).get,
          Some(HakemusRecord(Some("123.3"), HakemusOid("1234.3"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono1"))).map(h => h.dto(
            Set(),
            Some(TilankuvausRecord(123, EiTilankuvauksenTarkennetta, Some("textFi"), Some("textSv"), Some("textEn"))),
            List(TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.3"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)).dto)
            )).get
        )),
        ValintatapajonoRecord("arvonta", ValintatapajonoOid("valintatapajono2"), "valintatapajono2", 1, Some(10), Some(10), 1, true, true, true, None, 0, None, None, None, None, None, false, hakukohdeOid).dto(List(
          Some(HakemusRecord(Some("123.2"), HakemusOid("1234.2"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono2"))).map(h => h.dto(
            Set(),
            Some(TilankuvausRecord(123, EiTilankuvauksenTarkennetta, Some("textFi"), Some("textSv"), Some("textEn"))),
            List(TilaHistoriaRecord(ValintatapajonoOid("valintatapajono2"), HakemusOid("1234.2"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)).dto,
                 TilaHistoriaRecord(ValintatapajonoOid("valintatapajono2"), HakemusOid("1234.2"), Hylatty, new Date(now.minus(4, ChronoUnit.DAYS).toEpochMilli)).dto))
        ).get))
      ),
      List(HakijaryhmaRecord(1, "hakijaryhma1", "Hakijaryhma 1", Some(hakukohdeOid), 2, false, sijoitteluajoId, true, true, None, "uri/1/2/3").dto(List(HakemusOid("1234.1"))))
    )
  }

  trait SijoitteluServiceMocks extends Mockito with Scope with MustThrownExpectations {
    val audit = mock[Audit]

    val hakuService = mock[HakuService]
    hakuService.getHakukohde(hakukohdeOid) returns Right(Hakukohde(
      oid = hakukohdeOid,
      hakuOid = hakuOid,
      tarjoajaOids = Set(tarjoajaOid),
      koulutusAsteTyyppi = null,
      hakukohteenNimet = null,
      tarjoajaNimet = null,
      yhdenPaikanSaanto = null,
      tutkintoonJohtava = true,
      koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
      koulutuksenAlkamisvuosi = Some(2015),
      organisaatioRyhmaOids = Set(organisaatioRyhmaOid)))

    val authorizer = mock[OrganizationHierarchyAuthorizer]
    authorizer.checkAccess(session, Set(tarjoajaOid, organisaatioRyhmaOid), Set(Role.SIJOITTELU_READ, Role.SIJOITTELU_READ_UPDATE, Role.SIJOITTELU_CRUD)) returns Right(())

    type repositoryType = SijoitteluRepository with HakijaRepository with ValinnantulosRepository
    val sijoitteluRepository = mock[repositoryType]

    sijoitteluRepository.getLatestSijoitteluajoId("latest", hakuOid) returns Right(sijoitteluajoId)
    sijoitteluRepository.getSijoitteluajonHakukohde(sijoitteluajoId, hakukohdeOid) returns Some(SijoittelunHakukohdeRecord(sijoitteluajoId, hakukohdeOid, true))
    sijoitteluRepository.getHakukohteenValintatapajonot(sijoitteluajoId, hakukohdeOid) returns List(
      ValintatapajonoRecord("arvonta", ValintatapajonoOid("valintatapajono1"), "valintatapajono1", 1, Some(10), Some(10), 1, true, true, true, None, 0, None, None, None, None, None, false, hakukohdeOid),
      ValintatapajonoRecord("arvonta", ValintatapajonoOid("valintatapajono2"), "valintatapajono2", 1, Some(10), Some(10), 1, true, true, true, None, 0, None, None, None, None, None, false, hakukohdeOid)
    )
    sijoitteluRepository.getHakukohteenHakemukset(sijoitteluajoId, hakukohdeOid) returns List(
      HakemusRecord(Some("123.1"), HakemusOid("1234.1"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono1")),
      HakemusRecord(Some("123.2"), HakemusOid("1234.2"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono2")),
      HakemusRecord(Some("123.3"), HakemusOid("1234.3"), None, 1, 1, 1, Hyvaksytty, 123, None, false, None, false, false, ValintatapajonoOid("valintatapajono1"))
    )

    sijoitteluRepository.getHakukohteenTilahistoriat(sijoitteluajoId, hakukohdeOid) returns List(
      TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.1"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)),
      TilaHistoriaRecord(ValintatapajonoOid("valintatapajono2"), HakemusOid("1234.2"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)),
      TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.3"), Hyvaksytty, new Date(now.minus(2, ChronoUnit.DAYS).toEpochMilli)),
      TilaHistoriaRecord(ValintatapajonoOid("valintatapajono1"), HakemusOid("1234.1"), Hylatty, new Date(now.minus(4, ChronoUnit.DAYS).toEpochMilli)),
      TilaHistoriaRecord(ValintatapajonoOid("valintatapajono2"), HakemusOid("1234.2"), Hylatty, new Date(now.minus(4, ChronoUnit.DAYS).toEpochMilli))
    )

    sijoitteluRepository.getHakukohteenHakijaryhmat(sijoitteluajoId, hakukohdeOid) returns List(
      HakijaryhmaRecord(1, "hakijaryhma1", "Hakijaryhma 1", Some(hakukohdeOid), 2, false, sijoitteluajoId, true, true, None, "uri/1/2/3")
    )

    sijoitteluRepository.getSijoitteluajonHakijaryhmanHakemukset(sijoitteluajoId, "hakijaryhma1") returns List(HakemusOid("1234.1"))
    sijoitteluRepository.getSijoitteluajonHakijaryhmistaHyvaksytytHakemukset(sijoitteluajoId, List("hakijaryhma1")) returns Map("hakijaryhma1" -> List(HakemusOid("1234.1")))
    sijoitteluRepository.getSijoitteluajonHakijaryhmienHakemukset(sijoitteluajoId, List("hakijaryhma1")) returns Map("hakijaryhma1" -> List(HakemusOid("1234.1")))

    sijoitteluRepository.getValinnantilanKuvaukset(List(123)) returns
      Map(123 -> TilankuvausRecord(123, EiTilankuvauksenTarkennetta, Some("textFi"), Some("textSv"), Some("textEn")))

    sijoitteluRepository.getHakukohteenHakijaryhmat(sijoitteluajoId, hakukohdeOid) returns List(
      HakijaryhmaRecord(1, "hakijaryhma1", "Hakijaryhma 1", Some(hakukohdeOid), 2, false, sijoitteluajoId, true, true, None, "uri/1/2/3")
    )

    val service = new SijoitteluService(sijoitteluRepository, authorizer, hakuService, audit)


  }
}
