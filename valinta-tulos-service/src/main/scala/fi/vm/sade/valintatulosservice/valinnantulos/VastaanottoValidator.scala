package fi.vm.sade.valintatulosservice.valinnantulos

import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Date

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
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
  val ohjausparametrit: Ohjausparametrit

  val sitovaTaiEhdollinenVastaanotto: Seq[ValintatuloksenTila] = List(ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI)
  val keskenTaiVastaanottanutToisenPaikan: Seq[ValintatuloksenTila] = List(ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, ValintatuloksenTila.KESKEN)
  val keskenTaiEhdollisestiVastaanottanut: Seq[ValintatuloksenTila] = List(ValintatuloksenTila.KESKEN, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT)
  val virkailijanHyvaksytytTilat: Seq[Valinnantila] = List(Perunut, Peruutettu, Hyvaksytty, VarasijaltaHyvaksytty)

  def error(valinnantulos:Valinnantulos, msg:String) = DBIO.successful(Left(new ValinnantulosUpdateStatus(400, msg, valinnantulos.valintatapajonoOid, valinnantulos.hakemusOid)))
  def right = DBIO.successful(Right(()))
  def julkaistavissa(valinnantulos: Valinnantulos):Boolean = valinnantulos.julkaistavissa.exists(_ == true) && tuloksetJulkaistavissa

  lazy val tuloksetJulkaistavissa = ohjausparametrit.tulostenJulkistusAlkaa.forall(_.isBeforeNow())

  def onkoEhdollisestiVastaanotettavissa(valinnantulos: Valinnantulos): DBIO[Boolean]

  def tarkistaVastaanottoDeadline(valinnantulos: Valinnantulos):DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    def ollaankoHyvaksymassaJaJulkaisemassa() = valinnantulos.isHyvaksytty && valinnantulos.julkaistavissa.exists(_ == true)

    def hakijanHyvaksyttyJaJulkaistuDate() = valinnantulosRepository.findHyvaksyttyJaJulkaistuDateForHenkiloAndHakukohdeDBIO(valinnantulos.henkiloOid, hakukohdeOid)
      .flatMap(hyvaksyttyJaJulkaistu => DBIO.successful(hyvaksyttyJaJulkaistu.orElse( if(ollaankoHyvaksymassaJaJulkaisemassa()) Some(OffsetDateTime.now()) else None )))

    hakijanHyvaksyttyJaJulkaistuDate().flatMap(hyvaksyttyJaJulkaistu => laskeVastaanottoDeadline(ohjausparametrit, hyvaksyttyJaJulkaistu).map(_.toDate) match {
      case deadline if deadline.exists(_.after(new Date())) => error(valinnantulos,
        s"""Hakijakohtaista määräaikaa ${new SimpleDateFormat("dd-MM-yyyy").format(deadline)}
              kohteella ${hakukohdeOid} : ${valinnantulos.vastaanottotila} ei ole vielä ohitettu.""")
      case _ => right
    })
  }

  def validateVastaanotto(uusi: Valinnantulos, vanha: Valinnantulos): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = validateVastaanotto(uusi, Some(vanha))

  def validateVastaanotto(uusi: Valinnantulos, vanha: Option[Valinnantulos]): DBIO[Either[ValinnantulosUpdateStatus, Unit]] = {
    def left = error(uusi, _:String)

    (vanha.map(_.vastaanottotila).getOrElse(ValintatuloksenTila.KESKEN), uusi.vastaanottotila) match {
      case (_, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN) => right
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) if keskenTaiVastaanottanutToisenPaikan.contains(x) => right
      case (ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN, x) => left(s"Hakija on vastaanottanut toisen paikan")
      case (_, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) => tarkistaVastaanottoDeadline(uusi)
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
