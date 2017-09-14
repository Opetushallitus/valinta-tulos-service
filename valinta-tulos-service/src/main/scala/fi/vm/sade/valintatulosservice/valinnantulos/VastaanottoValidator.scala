package fi.vm.sade.valintatulosservice.valinnantulos

import java.text.SimpleDateFormat
import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.vastaanotto.VastaanottoUtils.laskeVastaanottoDeadline
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import slick.dbio.DBIO
import scala.concurrent.ExecutionContext.Implicits.global

trait VastaanottoValidator {
  val haku: Haku
  val hakukohdeOid: HakukohdeOid
  val valinnantulosRepository: ValinnantulosRepository with HakijaVastaanottoRepository
  val ohjausparametrit: Option[Ohjausparametrit]

  val sitovaTaiEhdollinenVastaanotto = List(ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
  val keskenTaiVastaanottanutToisenPaikan = List(ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, ValintatuloksenTila.KESKEN)
  val keskenTaiEhdollisestiVastaanottanut = List(ValintatuloksenTila.KESKEN, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
  val virkailijanHyvaksytytTilat = List(Perunut, Peruutettu, Hyvaksytty, HyvaksyttyVarasijalta)

  def error(valinnantulos:Valinnantulos, msg:String) = DBIO.successful(new ValinnantulosUpdateStatus(400, msg, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid))
  def ok(valinnantulos: Valinnantulos) = DBIO.successful(new ValinnantulosUpdateStatus(200, "ok", valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid))
  def julkaistavissa(valinnantulos: Valinnantulos):Boolean = valinnantulos.julkaistavissa.exists(_ == true) && tuloksetJulkaistavissa

  lazy val hyvaksyttyJaJulkaistuDatesForHakukohde = valinnantulosRepository.findHyvaksyttyJulkaistuDatesForHakukohde(hakukohdeOid)
  lazy val tuloksetJulkaistavissa = ohjausparametrit.flatMap(_.tulostenJulkistusAlkaa).map(_.isBeforeNow()).getOrElse(ohjausparametrit.isDefined)

  def onkoEhdollisestiVastaanotettavissa(valinnantulos: Valinnantulos): DBIO[Boolean]

  def validateVastaanotto(uusi: Valinnantulos, vanha: Valinnantulos): DBIO[ValinnantulosUpdateStatus] = validateVastaanotto(uusi, Some(vanha))

  def validateVastaanotto(uusi: Valinnantulos, vanha: Option[Valinnantulos]): DBIO[ValinnantulosUpdateStatus] = {
    def left = error(uusi, _:String)
    def right = ok(uusi)

    lazy val henkilönVastaanottoDeadline = laskeVastaanottoDeadline(ohjausparametrit, hyvaksyttyJaJulkaistuDatesForHakukohde.get(uusi.henkiloOid)).map(_.toDate)

    (vanha.map(_.vastaanottotila).getOrElse(ValintatuloksenTila.KESKEN), uusi.vastaanottotila) match {
      case (_, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN) => right
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) if keskenTaiVastaanottanutToisenPaikan.contains(x) => right
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) => left(s"Hakija on vastaanottanut toisen paikan")
      case (_, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) if henkilönVastaanottoDeadline.exists(_.after(new Date())) => left(s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(henkilönVastaanottoDeadline)} kohteella ${hakukohdeOid} : ${uusi.vastaanottotila} ei ole vielä ohitettu.""")
      case (_, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) => right
      case (_, u) if !sitovaTaiEhdollinenVastaanotto.contains(u) => right
      case (v, u) if v == u => right
      case (_, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT) => onkoEhdollisestiVastaanotettavissa(uusi).flatMap(ehdollisestiVastaanotettavissa => ehdollisestiVastaanotettavissa match {
        case false => left(s"Hakutoivetta ei voi ottaa ehdollisesti vastaan")
        case true => right
      })
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) if !virkailijanHyvaksytytTilat.contains(uusi.valinnantila) => left(s"""Ei voi tallentaa vastaanottotietoa, koska hakijalle näytettävä tila on "${uusi.valinnantila}"""")
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) if !julkaistavissa(uusi) => left(s"""Ei voi tallentaa vastaanottotietoa, koska hakemuksen valinnantulokset eivät ole julkaistavissa""")
      case (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) => right
    }
  }
}
