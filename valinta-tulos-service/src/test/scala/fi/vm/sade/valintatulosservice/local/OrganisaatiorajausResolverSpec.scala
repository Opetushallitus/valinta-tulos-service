package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.security.{EiRajausta, OrganisaatiorajausResolver, OrganizationHierarchyAuthorizer, RajattuOrganisaatioihin}
import fi.vm.sade.valintatulosservice.organisaatio.{Organisaatio, OrganisaatioService, Organisaatiot}
import fi.vm.sade.valintatulosservice.security.{CasSession, Role, ServiceTicket}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.Scope

@RunWith(classOf[JUnitRunner])
class OrganisaatiorajausResolverSpec extends Specification with Mockito {

  val root = "1.2.246.562.10.00000000001"
  val orgA = "1.2.246.562.10.111"
  val orgALapsi = "1.2.246.562.10.112"
  val orgB = "1.2.246.562.10.222"
  val orgASisar = "1.2.246.562.10.113"
  val ryhma = "1.2.246.562.28.333"
  val roolit: Set[Role] = Set(Role.VALINTATULOSSERVICE_CRUD)

  def organisaatio(oid: String, children: Organisaatio*): Organisaatio = Organisaatio(oid, Map.empty, None, List.empty, "AKTIIVINEN", children)
  // Organisaatiopalvelun hierarkiahaku palauttaa polun juuresta kysyttyyn organisaatioon ja sen alipuun
  def hierarkia(kysytty: Organisaatio, sisarukset: Organisaatio*): Organisaatiot =
    Organisaatiot(List(organisaatio(root, (kysytty +: sisarukset): _*)))
  def session(roles: Role*): CasSession = CasSession(ServiceTicket("t"), "1.2.246.562.24.1", roles.toSet)
  def roleIn(oid: String): Role = Role(s"${Role.VALINTATULOSSERVICE_CRUD.s}_$oid")

  trait Mocks extends Scope {
    val organisaatioService: OrganisaatioService = mock[OrganisaatioService]
    val authorizer: OrganizationHierarchyAuthorizer = mock[OrganizationHierarchyAuthorizer]
    authorizer.hakukohteetHakukohderyhmista(any, any) returns Set.empty[HakukohdeOid]
    val resolver = new OrganisaatiorajausResolver(organisaatioService, authorizer, root)
  }

  "OrganisaatiorajausResolver" should {
    "ei rajaa juuriorganisaation oikeudella" in new Mocks {
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(root)), roolit) must_== EiRajausta
      there was no(organisaatioService).hae(any)
    }

    "laajentaa käyttäjän organisaation aliorganisaatioihin" in new Mocks {
      organisaatioService.hae(orgA) returns Right(hierarkia(organisaatio(orgA, organisaatio(orgALapsi))))
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgA)), roolit) must_==
        RajattuOrganisaatioihin(Set(orgA, orgALapsi), Set.empty)
    }

    "ei anna oikeutta hierarkiavastauksen esivanhempiin eikä sisarorganisaatioihin" in new Mocks {
      organisaatioService.hae(orgALapsi) returns Right(Organisaatiot(List(
        organisaatio(root, organisaatio(orgA, organisaatio(orgALapsi), organisaatio(orgASisar))))))
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgALapsi)), roolit) must_==
        RajattuOrganisaatioihin(Set(orgALapsi), Set.empty)
    }

    "epäonnistuu, jos kysyttyä organisaatiota ei ole hierarkiavastauksessa" in new Mocks {
      organisaatioService.hae(orgA) returns Right(Organisaatiot(List(organisaatio(root, organisaatio(orgB)))))
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgA)), roolit) must throwAn[IllegalStateException]
    }

    "huomioi vain annettujen roolien organisaatiot" in new Mocks {
      organisaatioService.hae(orgA) returns Right(hierarkia(organisaatio(orgA)))
      val s = session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgA), Role(s"${Role.SIJOITTELU_READ.s}_$orgB"))
      resolver.resolve(s, roolit) must_== RajattuOrganisaatioihin(Set(orgA), Set.empty)
      there was no(organisaatioService).hae(orgB)
    }

    "ei tulkitse juurioikeutta toisesta roolista" in new Mocks {
      organisaatioService.hae(orgA) returns Right(hierarkia(organisaatio(orgA)))
      val s = session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgA), Role.SIJOITTELU_CRUD_OPH)
      resolver.resolve(s, roolit) must_== RajattuOrganisaatioihin(Set(orgA), Set.empty)
    }

    "lisää hakukohderyhmien hakukohteet" in new Mocks {
      val hakukohde = HakukohdeOid("1.2.246.562.20.1")
      authorizer.hakukohteetHakukohderyhmista(any, any) returns Set(hakukohde)
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(ryhma)), roolit) must_==
        RajattuOrganisaatioihin(Set.empty, Set(hakukohde))
      there was no(organisaatioService).hae(any)
    }

    "rajaa kaiken pois, jos roolilla ei ole organisaatiota" in new Mocks {
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD), roolit) must_== RajattuOrganisaatioihin(Set.empty, Set.empty)
    }

    "epäonnistuu, jos organisaatiohierarkiaa ei saada" in new Mocks {
      organisaatioService.hae(orgA) returns Left(new RuntimeException("down"))
      resolver.resolve(session(Role.VALINTATULOSSERVICE_CRUD, roleIn(orgA)), roolit) must throwAn[IllegalStateException]
    }
  }

  "RajattuOrganisaatioihin" should {
    "sallii hakukohteen tarjoajan, organisaatioryhmän tai hakukohderyhmän perusteella" in {
      val hk1 = HakukohdeOid("1.2.246.562.20.1")
      val hk2 = HakukohdeOid("1.2.246.562.20.2")
      val rajaus = RajattuOrganisaatioihin(Set(orgALapsi), Set(hk2))
      rajaus.salliiHakukohteen(hk1, Set(orgALapsi)) must beTrue
      rajaus.salliiHakukohteen(hk1, Set(orgB)) must beFalse
      rajaus.salliiHakukohteen(hk2, Set(orgB)) must beTrue
    }
  }
}
