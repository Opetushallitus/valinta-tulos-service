package fi.vm.sade.security

import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.organisaatio.OrganisaatioService
import fi.vm.sade.valintatulosservice.security.{Role, Session}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakukohdeOid

/**
 * Kuvaa, mihin organisaatioihin ja hakukohteisiin käyttäjän tulosten katselu on rajattu.
 */
sealed trait Organisaatiorajaus

/** Käyttäjällä on oikeus juuriorganisaatiossa, tuloksia ei rajata. */
case object EiRajausta extends Organisaatiorajaus

/**
 * Tulokset rajataan hakukohteisiin, joiden tarjoaja tai organisaatioryhmä on jokin annetuista organisaatioista
 * (käyttäjän organisaatiot aliorganisaatioineen) tai jotka kuuluvat johonkin käyttäjän hakukohderyhmistä.
 */
case class RajattuOrganisaatioihin(organisaatioOidit: Set[String], hakukohdeOidit: Set[HakukohdeOid]) extends Organisaatiorajaus {
  def salliiHakukohteen(hakukohdeOid: HakukohdeOid, hakukohteenOrganisaatiot: => Set[String]): Boolean =
    hakukohdeOidit.contains(hakukohdeOid) || hakukohteenOrganisaatiot.exists(organisaatioOidit.contains)
}

/**
 * Päättelee käyttäjän session rooleista, mihin organisaatioihin tulosten katselu rajataan.
 *
 * Toisin kuin OrganizationHierarchyAuthorizer, joka tarkistaa yksittäisen kohdeorganisaation oikeudet kulkemalla
 * hierarkiaa ylöspäin, tämä laajentaa käyttäjän organisaatiot alaspäin kaikkiin aliorganisaatioihin, jotta koko haun
 * kattava tulosjoukko voidaan suodattaa muistissa.
 */
class OrganisaatiorajausResolver(organisaatioService: OrganisaatioService,
                                 authorizer: OrganizationHierarchyAuthorizer,
                                 rootOrganisaatioOid: String) extends Logging {

  private val organisaatioOidPrefix = "1.2.246.562.10."

  def resolve(session: Session, roles: Set[Role]): Organisaatiorajaus = {
    val roolienOidit = session.roles
      .filter(sessionRole => roles.exists(role => sessionRole.getString.startsWith(role.getString + "_")))
      .flatMap(_.getOidString)
    val organisaatioOidit = roolienOidit.filter(_.startsWith(organisaatioOidPrefix))

    if (organisaatioOidit.contains(rootOrganisaatioOid)) {
      EiRajausta
    } else {
      val laajennetut = organisaatioOidit.flatMap(aliorganisaatioineen)
      val hakukohteet = authorizer.hakukohteetHakukohderyhmista(session, roles)
      if (laajennetut.isEmpty && hakukohteet.isEmpty) {
        logger.warn(s"Käyttäjällä ${session.personOid} ei ole rooleja $roles yhdessäkään organisaatiossa tai hakukohderyhmässä, tuloksia ei palauteta")
      }
      RajattuOrganisaatioihin(laajennetut, hakukohteet)
    }
  }

  /**
   * Organisaatiopalvelun hierarkiahaku palauttaa polun juuresta kysyttyyn organisaatioon sekä sen alipuun. Vain
   * kysytyn organisaation alipuu kelpaa: esivanhempien mukaan ottaminen antaisi oikeuden myös sisarorganisaatioihin.
   */
  private def aliorganisaatioineen(oid: String): Set[String] = organisaatioService.hae(oid) match {
    case Right(organisaatiot) => organisaatiot.find(_.oid == oid)
      .map(_.kaikkiOidit)
      .getOrElse(throw new IllegalStateException(s"Organisaatiota $oid ei löytynyt organisaatiopalvelun hierarkiasta, tuloksia ei voida rajata"))
    case Left(e) => throw new IllegalStateException(s"Organisaation $oid hierarkian haku epäonnistui, tuloksia ei voida rajata", e)
  }
}
