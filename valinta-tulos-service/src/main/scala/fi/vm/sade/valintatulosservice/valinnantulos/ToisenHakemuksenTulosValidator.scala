package fi.vm.sade.valintatulosservice.valinnantulos

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Henkilöllä voi olla hakukohteessa vain yksi voimassa oleva vastaanotto ja yksi ilmoittautuminen, mutta valinnantulos
 * näyttää ne vain sille hakemukselle, jolle ne on tallennettu. Jos henkilöllä on samaan hakukohteeseen toinen hakemus,
 * jolla vastaanotto tai ilmoittautuminen jo on, tämän hakemuksen vastaanoton tai ilmoittautumisen muutos korvaisi sen
 * huomaamatta. Muutos hylätään ennen kuin mitään on tallennettu, jotta tallennus ei jää osittaiseksi.
 */
object ToisenHakemuksenTulosValidator {
  def validate(repository: ValinnantulosRepository with HakijaVastaanottoRepository,
               uusi: Valinnantulos,
               vanha: Option[Valinnantulos]): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    val vastaanottoMuuttuu = uusi.vastaanottotila != vanha.map(_.vastaanottotila).getOrElse(ValintatuloksenTila.KESKEN)
    val ilmoittautuminenMuuttuu = uusi.ilmoittautumistila != vanha.map(_.ilmoittautumistila).getOrElse(EiTehty)
    if (uusi.poistettava.getOrElse(false) || !(vastaanottoMuuttuu || ilmoittautuminenMuuttuu)) {
      DBIO.successful(Right(()))
    } else {
      for {
        vastaanotto <- repository.findToisenHakemuksenVastaanotto(uusi.henkiloOid, uusi.hakukohdeOid, uusi.hakemusOid)
        ilmoittautuminen <- repository.findToisenHakemuksenIlmoittautuminen(uusi.henkiloOid, uusi.hakukohdeOid, uusi.hakemusOid)
      } yield (vastaanotto, ilmoittautuminen) match {
        case (Some(toinen), _) => Left(status(uusi, ToisenHakemuksenTulosException.Vastaanotto, toinen))
        case (_, Some(toinen)) => Left(status(uusi, ToisenHakemuksenTulosException.Ilmoittautuminen, toinen))
        case _ => Right(())
      }
    }
  }

  private def status(uusi: Valinnantulos, tulos: String, toinenHakemusOid: HakemusOid): ValinnantulosUpdateStatus =
    ValinnantulosUpdateStatus(409,
      ToisenHakemuksenTulosException(uusi.henkiloOid, uusi.hakukohdeOid, uusi.hakemusOid, toinenHakemusOid, tulos).getMessage,
      uusi.valintatapajonoOid, uusi.hakemusOid)
}
