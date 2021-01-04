package fi.vm.sade.valintatulosservice.valintarekisteri

import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.tarjonta.{HakuService, Hakukohde, YhdenPaikanSaanto}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{
  VastaanottoRecord,
  VirkailijaVastaanottoRepository
}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import org.junit.runner.RunWith
import org.specs2.matcher.MustThrownExpectations
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class YhdenPaikanSaannosSpec extends Specification {
  private val hakuOid = HakuOid("hakuOid")
  private val otherHakuOid = HakuOid("otherHakuOid")
  private val hakukohdeOid = HakukohdeOid("hakukohdeOid")
  private val otherHakukohdeOid = HakukohdeOid("otherHakukohdeOid")
  private val hakemusOid = HakemusOid("hakemusOid")
  private val otherHakemusOid = HakemusOid("otherHakemusOid")
  private val henkiloOid = "henkiloOid"
  private val kausi = Kevat(2000)
  private val hakukohde = Hakukohde(
    oid = hakukohdeOid,
    hakuOid = hakuOid,
    tarjoajaOids = Set(),
    organisaatioRyhmaOids = Set(),
    koulutusAsteTyyppi = "KORKEAKOULUTUS",
    hakukohteenNimet = Map(),
    tarjoajaNimet = Map(),
    yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = true, syy = "yhden paikan säännös voimassa"),
    tutkintoonJohtava = true,
    koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
    koulutuksenAlkamisvuosi = Some(2000)
  )
  private val nonYpsHakukohde = Hakukohde(
    oid = hakukohdeOid,
    hakuOid = hakuOid,
    tarjoajaOids = Set(),
    organisaatioRyhmaOids = Set(),
    koulutusAsteTyyppi = "KORKEAKOULUTUS",
    hakukohteenNimet = Map(),
    tarjoajaNimet = Map(),
    yhdenPaikanSaanto = YhdenPaikanSaanto(voimassa = false, syy = "ei tutkintoonjohtava"),
    tutkintoonJohtava = false,
    koulutuksenAlkamiskausiUri = Some("kausi_k#1"),
    koulutuksenAlkamisvuosi = Some(2000)
  )
  private val valinnantulos = Valinnantulos(
    hakukohdeOid = hakukohdeOid,
    valintatapajonoOid = ValintatapajonoOid("valintatapajonoOid"),
    hakemusOid = hakemusOid,
    henkiloOid = henkiloOid,
    valinnantila = Hyvaksytty,
    ehdollisestiHyvaksyttavissa = Some(false),
    ehdollisenHyvaksymisenEhtoKoodi = None,
    ehdollisenHyvaksymisenEhtoFI = None,
    ehdollisenHyvaksymisenEhtoSV = None,
    ehdollisenHyvaksymisenEhtoEN = None,
    valinnantilanKuvauksenTekstiFI = None,
    valinnantilanKuvauksenTekstiSV = None,
    valinnantilanKuvauksenTekstiEN = None,
    julkaistavissa = Some(true),
    hyvaksyttyVarasijalta = Some(false),
    hyvaksyPeruuntunut = Some(false),
    vastaanottotila = ValintatuloksenTila.KESKEN,
    ilmoittautumistila = EiTehty
  )
  private val vastaanotonHakukohde = YPSHakukohde(
    oid = otherHakukohdeOid,
    hakuOid = otherHakuOid,
    koulutuksenAlkamiskausi = kausi
  )
  private val sitovaVastaanotto = VastaanottoRecord(
    henkiloOid = henkiloOid,
    hakuOid = otherHakuOid,
    hakukohdeOid = otherHakukohdeOid,
    action = VastaanotaSitovasti,
    ilmoittaja = "ilmoittaja",
    timestamp = new Date()
  )
  private val ehdollinenVastaanotto = VastaanottoRecord(
    henkiloOid = henkiloOid,
    hakuOid = otherHakuOid,
    hakukohdeOid = otherHakukohdeOid,
    action = VastaanotaEhdollisesti,
    ilmoittaja = "ilmoittaja",
    timestamp = new Date()
  )

  "YhdenPaikanSaannos" in {
    "sets vastaanottotila to OTTANUT_VASTAAN_TOISEN_PAIKAN" in {
      "if hyväksytty, vastaanottamatta" in {
        val v = valinnantulos.copy(
          valinnantila = Hyvaksytty,
          vastaanottotila = ValintatuloksenTila.KESKEN
        )
        "and sitova vastaanotto exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (hakemusOid, vastaanotonHakukohde, sitovaVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
        "and ehdollinen vastaanotto for other hakemus exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (otherHakemusOid, vastaanotonHakukohde, ehdollinenVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
      }
      "if hyväksytty varasijalta, vastaanottamatta" in {
        val v = valinnantulos.copy(
          valinnantila = VarasijaltaHyvaksytty,
          vastaanottotila = ValintatuloksenTila.KESKEN
        )
        "and sitova vastaanotto exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (hakemusOid, vastaanotonHakukohde, sitovaVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
        "and ehdollinen vastaanotto for other hakemus exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (otherHakemusOid, vastaanotonHakukohde, ehdollinenVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
      }
      "if varalla, vastaanottamatta" in {
        val v = valinnantulos.copy(
          valinnantila = Varalla,
          vastaanottotila = ValintatuloksenTila.KESKEN
        )
        "and sitova vastaanotto exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (hakemusOid, vastaanotonHakukohde, sitovaVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
        "and ehdollinen vastaanotto for other hakemus exists" in new YhdenPaikanSaannosWithMocks {
          hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
          virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
            (otherHakemusOid, vastaanotonHakukohde, ehdollinenVastaanotto)
          )
          yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(
            Right(Set(v.copy(vastaanottotila = ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)))
          )
        }
      }
    }
    "don't set vastaanottotila if yhden paikan säännös not voimassa" in new YhdenPaikanSaannosWithMocks {
      private val v = valinnantulos.copy(
        valinnantila = Hyvaksytty,
        vastaanottotila = ValintatuloksenTila.KESKEN
      )
      hakuService.getHakukohde(v.hakukohdeOid) returns Right(nonYpsHakukohde)
      yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(Right(Set(v)))
    }
    "don't set vastaanottotila if no vastaanotto exists" in new YhdenPaikanSaannosWithMocks {
      private val v = valinnantulos.copy(
        valinnantila = Hyvaksytty,
        vastaanottotila = ValintatuloksenTila.KESKEN
      )
      hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
      virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set()
      yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(Right(Set(v)))
    }
    "don't set vastaanottotila if ehdollinen vastaanotto for this hakemus" in new YhdenPaikanSaannosWithMocks {
      private val v = valinnantulos.copy(
        valinnantila = Hyvaksytty,
        vastaanottotila = ValintatuloksenTila.KESKEN
      )
      hakuService.getHakukohde(v.hakukohdeOid) returns Right(hakukohde)
      virkailijaVastaanottoRepository.findYpsVastaanotot(kausi, Set(v.henkiloOid)) returns Set(
        (v.hakemusOid, vastaanotonHakukohde, ehdollinenVastaanotto)
      )
      yhdenPaikanSaannos.apply(Set(v)) must beEqualTo(Right(Set(v)))
    }
  }

  trait YhdenPaikanSaannosWithMocks extends Mockito with Scope with MustThrownExpectations {
    val hakuService = mock[HakuService]
    val virkailijaVastaanottoRepository = mock[VirkailijaVastaanottoRepository]
    val yhdenPaikanSaannos = new YhdenPaikanSaannos(hakuService, virkailijaVastaanottoRepository)
  }

}
