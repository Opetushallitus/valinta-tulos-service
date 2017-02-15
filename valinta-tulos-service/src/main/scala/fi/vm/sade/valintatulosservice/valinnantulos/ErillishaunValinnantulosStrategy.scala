package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ValinnantulosRepository
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantuloksenMuokkaus, ValinnantulosUpdateStatus}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class ErillishaunValinnantulosStrategy(auditInfo: AuditInfo,
                                       valinnantulosRepository: ValinnantulosRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       ifUnmodifiedSince: Instant,
                                       audit: Audit) extends ValinnantulosStrategy with Logging {
  private val session = auditInfo.session._2

  def hasChange(uusi:Valinnantulos, vanha:Valinnantulos) = uusi.hasChanged(vanha) || uusi.poistettava.getOrElse(false)

  def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {
    def validateTilat() = {
      def ilmoittautunut(ilmoittautuminen: SijoitteluajonIlmoittautumistila) = ilmoittautuminen != EiTehty
      def hyvaksytty(tila: Valinnantila) = List(Hyvaksytty, HyvaksyttyVarasijalta).contains(tila) //TODO entäs täyttyjonosäännöllä hyväksytty?
      def vastaanotto(vastaanotto: VastaanottoAction) = vastaanotto != Poista
      def vastaanottoEiMyohastynyt(vastaanotto: VastaanottoAction) = vastaanotto != Poista && vastaanotto != MerkitseMyohastyneeksi
      def hylattyTaiVaralla(tila: Valinnantila) = Hylatty == tila || Varalla == tila
      def vastaanottaneena(tila: VastaanottoAction) = List(VastaanotaSitovasti, VastaanotaEhdollisesti, MerkitseMyohastyneeksi).contains(tila)
      def peruneena(tila: Valinnantila) = List(Perunut, Peruuntunut, Peruutettu).contains(tila)
      def keskenTaiPerunut(tila: VastaanottoAction) = List(Poista, Peruuta, Peru, MerkitseMyohastyneeksi).contains(tila)

      (uusi.valinnantila, uusi.vastaanottotila, uusi.ilmoittautumistila) match {
        case (t, v, i) if ilmoittautunut(i) && !(hyvaksytty(t) && vastaanotto(v)) => Left(ValinnantulosUpdateStatus(409,
          s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if hylattyTaiVaralla(t) && (vastaanottaneena(v) || ilmoittautunut(i)) => Left(ValinnantulosUpdateStatus(409,
          s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if peruneena(t) && !keskenTaiPerunut(v) => Left(ValinnantulosUpdateStatus(409,
          s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if vastaanottoEiMyohastynyt(v) && !hyvaksytty(t) => Left(ValinnantulosUpdateStatus(409,
          s"Vastaanottaneen tai peruneen hakijan tulisi olla hyväksyttynä", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, _, _) => Right()
      }
    }

    def validatePoisto() = (uusi.poistettava.getOrElse(false), vanha) match {
      case (true, None) => Left(ValinnantulosUpdateStatus(404,
        s"Valinnantulosta ei voida poistaa, koska sitä ei ole olemassa", uusi.valintatapajonoOid, uusi.hakemusOid))
      case (_, _) => Right()
    }

    def validateMuutos() = {
      for {
        poisto <- validatePoisto.right
        tilat <- validateTilat.right
      } yield tilat
    }

    validateMuutos()
  }

  def save(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): DBIO[Unit] = {
    val muokkaaja = session.personOid
    val selite = "Erillishaun tallennus"

    def createInsertOperations = {
      List(
        Some(valinnantulosRepository.storeValinnantila(uusi.getValinnantilanTallennus(muokkaaja), Some(ifUnmodifiedSince))),
        Some(valinnantulosRepository.storeValinnantuloksenOhjaus(uusi.getValinnantuloksenOhjaus(muokkaaja, selite), Some(ifUnmodifiedSince))),
        Option(uusi.ilmoittautumistila != EiTehty).collect { case true => valinnantulosRepository.storeIlmoittautuminen(
          uusi.henkiloOid, Ilmoittautuminen(uusi.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), Some(ifUnmodifiedSince))
        }
      ).flatten
    }

    def createUpdateOperations(vanha: Valinnantulos) = {
      List(
        Option(uusi.valinnantila != vanha.valinnantila).collect { case true =>
          valinnantulosRepository.storeValinnantila(uusi.getValinnantilanTallennus(muokkaaja), Some(ifUnmodifiedSince))
        },
        Option(uusi.hasOhjausChanged(vanha)).collect { case true => valinnantulosRepository.updateValinnantuloksenOhjaus(
          uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, selite), Some(ifUnmodifiedSince))
        },
        Option(uusi.ilmoittautumistila != vanha.ilmoittautumistila).collect { case true => valinnantulosRepository.storeIlmoittautuminen(
          vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), Some(ifUnmodifiedSince))
        }
      ).flatten
    }

    def createDeleteOperations(vanha:Valinnantulos) = {
      List(
        Some(valinnantulosRepository.deleteValinnantulos(muokkaaja, uusi, Some(ifUnmodifiedSince))),
        Option(vanha.ilmoittautumistila != EiTehty).collect { case true => valinnantulosRepository.deleteIlmoittautuminen(
          uusi.henkiloOid, Ilmoittautuminen(uusi.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), Some(ifUnmodifiedSince)
        )}
      ).flatten
    }

    val operations = ( uusi.poistettava.getOrElse(false), vanhaOpt ) match {
      case (false, None) => logger.info(s"Käyttäjä $muokkaaja lisäsi " +
        s"hakemukselle ${uusi.hakemusOid} valinnantuloksen erillishaun valintatapajonossa ${uusi.valintatapajonoOid}:" +
        s"vastaanottotila on ${uusi.vastaanottotila} ja " +
        s"valinnantila on ${uusi.valinnantila} ja " +
        s"ilmoittautumistila on ${uusi.ilmoittautumistila}.")
        hakukohdeRecordService.getHakukohdeRecord(uusi.hakukohdeOid) match {
          case Right(_) => createInsertOperations
          case Left(t) => List(DBIO.failed(t))
        }

      case (false, Some(vanha)) => logger.info(s"Käyttäjä ${muokkaaja} muokkasi " +
        s"hakemuksen ${uusi.hakemusOid} valinnan tulosta erillishaun valintatapajonossa ${uusi.valintatapajonoOid}" +
        s"valinnantilasta ${vanha.valinnantila} tilaan ${uusi.valinnantila} ja " +
        s"vastaanottotilasta ${vanha.vastaanottotila} tilaan ${uusi.vastaanottotila} ja " +
        s"ilmoittautumistilasta ${vanha.ilmoittautumistila} tilaan ${uusi.ilmoittautumistila}.")
        createUpdateOperations(vanha)

      case (true, Some(vanha)) => logger.info(s"Käyttäjä ${muokkaaja} poisti " +
        s"hakemuksen ${uusi.hakemusOid} valinnan tuloksen erillishaun valintatapajonossa ${uusi.valintatapajonoOid}" +
        s"vastaanottotila on ${vanha.vastaanottotila} ja " +
        s"valinnantila on ${vanha.valinnantila} ja " +
        s"ilmoittautumistila on ${vanha.ilmoittautumistila}.")
        createDeleteOperations(vanha)

      case (true, None) => logger.warn(s"Käyttäjä ${muokkaaja} yritti poistaa " +
        s"hakemuksen ${uusi.hakemusOid} valinnan tuloksen erillishaun valintatapajonossa ${uusi.valintatapajonoOid}" +
        s"mutta sitä ei ole olemassa.")
        List(DBIO.failed(new InternalError(s"Käyttäjä ${muokkaaja} yritti poistaa " +
          s"hakemuksen ${uusi.hakemusOid} valinnan tuloksen erillishaun valintatapajonossa ${uusi.valintatapajonoOid}" +
          s"mutta sitä ei ole olemassa.")))
    }

    DBIO.sequence(operations).map(_ =>
      audit.log(auditInfo.user, ValinnantuloksenMuokkaus,
        new Target.Builder()
          .setField("hakukohde", uusi.hakukohdeOid)
          .setField("valintatapajono", uusi.valintatapajonoOid)
          .setField("hakemus", uusi.hakemusOid)
          .build(),
        new Changes.Builder()
          .updated("valinnantila", vanhaOpt.map(_.valinnantila.toString).getOrElse("Ei valinnantilaa"), uusi.valinnantila.toString)
          .updated("ehdollisestiHyvaksyttavissa", vanhaOpt.map(_.ehdollisestiHyvaksyttavissa).getOrElse(false).toString, uusi.ehdollisestiHyvaksyttavissa.getOrElse(false).toString)
          .updated("julkaistavissa", vanhaOpt.map(_.julkaistavissa).getOrElse(false).toString, uusi.julkaistavissa.getOrElse(false).toString)
          .updated("hyvaksyttyVarasijalta", vanhaOpt.map(_.hyvaksyttyVarasijalta).getOrElse(false).toString, uusi.hyvaksyttyVarasijalta.getOrElse(false).toString)
          .updated("hyvaksyPeruuntunut", vanhaOpt.map(_.hyvaksyPeruuntunut).getOrElse(false).toString, uusi.hyvaksyPeruuntunut.getOrElse(false).toString)
          .updated("vastaanottotila", vanhaOpt.map(_.vastaanottotila.toString).getOrElse("Ei vastaanottoa"), uusi.vastaanottotila.toString)
          .updated("ilmoittautumistila", vanhaOpt.map(_.ilmoittautumistila.toString).getOrElse("Ei ilmoittautumista"), uusi.ilmoittautumistila.toString)
          .build()
      )
    )
  }
}