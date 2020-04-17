package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.auditlog.{Audit, Changes, Target}
import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.ohjausparametrit.Ohjausparametrit
import fi.vm.sade.valintatulosservice.tarjonta.Haku
import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.{HyvaksynnanEhtoRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.db.{HakijaVastaanottoRepository, ValinnanTilanKuvausRepository, ValinnantulosRepository}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.hakukohde.HakukohdeRecordService
import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantuloksenLisays, ValinnantuloksenMuokkaus, ValinnantuloksenPoisto}
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global

class ErillishaunValinnantulosStrategy(auditInfo: AuditInfo,
                                       haku: Haku,
                                       hakukohdeOid: HakukohdeOid,
                                       ohjausparametrit: Ohjausparametrit,
                                       valinnantulosRepository: ValinnantulosRepository
                                         with HakijaVastaanottoRepository
                                         with ValinnanTilanKuvausRepository
                                         with HyvaksynnanEhtoRepository,
                                       hakukohdeRecordService: HakukohdeRecordService,
                                       ifUnmodifiedSince: Option[Instant],
                                       audit: Audit) extends ValinnantulosStrategy with Logging {
  private val session = auditInfo.session._2

  lazy val hakukohdeRecord: Either[Throwable, HakukohdeRecord] = hakukohdeRecordService.getHakukohdeRecord(hakukohdeOid)
  lazy val vastaanottoValidator = new ErillishaunVastaanottoValidator(haku, hakukohdeOid, ohjausparametrit, valinnantulosRepository)

  def hasChange(uusi:Valinnantulos, vanha:Valinnantulos) = uusi.hasChanged(vanha) || uusi.poistettava.getOrElse(false)

  def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {

    def validateValinnantila() = (uusi.valinnantila, uusi.vastaanottotila) match {
      case (Hylatty, ValintatuloksenTila.KESKEN) |
           (Varalla, ValintatuloksenTila.KESKEN) |
           (Peruuntunut, ValintatuloksenTila.KESKEN) |
           (Peruuntunut, ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN) |
           (Perunut, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA) |
           (VarasijaltaHyvaksytty, ValintatuloksenTila.KESKEN) |
           (VarasijaltaHyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT) |
           (VarasijaltaHyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) |
           (Hyvaksytty, ValintatuloksenTila.KESKEN) |
           (Hyvaksytty, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT) |
           (Hyvaksytty, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI) |
           (Perunut, ValintatuloksenTila.PERUNUT) |
           (Peruutettu, ValintatuloksenTila.PERUUTETTU) |
           (_, ValintatuloksenTila.KESKEN) => Right()
      case (_, _) if uusi.ohitaVastaanotto.getOrElse(false) => Right()
      case (_, _) => Left(ValinnantulosUpdateStatus(409,
        s"Hakemuksen tila ${uusi.valinnantila} ja vastaanotto ${uusi.vastaanottotila} ovat ristiriitaiset.", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

    def validateEhdollisestiHyvaksyttavissa() = {
      def notModified() = uusi.ehdollisestiHyvaksyttavissa.getOrElse(false) == vanha.exists(_.ehdollisestiHyvaksyttavissa.getOrElse(false))

      if(notModified() || uusi.ohitaVastaanotto.getOrElse(false)) {
        Right()
      } else if(haku.toinenAste) {
        Left(ValinnantulosUpdateStatus(409, s"Toisen asteen haussa ei voida hyväksyä ehdollisesti", uusi.valintatapajonoOid, uusi.hakemusOid))
      } else {
        Right()
      }
    }

    def validateJulkaistavissa() = {
      (vanha.flatMap(_.julkaistavissa), uusi.julkaistavissa) match {
        case (Some(true), None) | (Some(true), Some(false)) if uusi.ilmoittautumistila != EiTehty =>
          Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen ilmoittautumistila on ${uusi.ilmoittautumistila}", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (Some(true), None) | (Some(true), Some(false)) if uusi.vastaanottotila != ValintatuloksenTila.KESKEN =>
          Left(ValinnantulosUpdateStatus(409, s"Valinnantulosta ei voida merkitä ei-julkaistavaksi, koska sen vastaanottotila on ${uusi.vastaanottotila}", uusi.valintatapajonoOid, uusi.hakemusOid))
        case _ =>
          Right()
      }
    }

    def validateIlmoittautuminen() = (uusi.ilmoittautumistila, uusi.vastaanottotila, uusi.julkaistavissa) match {
      case (_, _, _) if uusi.ohitaIlmoittautuminen.getOrElse(false) => Right()
      case (x, _, _) if vanha.isDefined && vanha.get.ilmoittautumistila == x => Right()
      case (EiTehty, _, _) | (_, ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, Some(true)) => Right()
      case (_, _, _) => Left(ValinnantulosUpdateStatus(409,
        s"Ilmoittautumistila ${uusi.ilmoittautumistila} ei ole sallittu, kun vastaanotto on ${uusi.vastaanottotila} ja julkaistavissa tieto on ${uusi.julkaistavissa}", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

    def validateTilat() = {
      def ilmoittautunut(ilmoittautuminen: SijoitteluajonIlmoittautumistila) = ilmoittautuminen != EiTehty
      def hyvaksytty(tila: Valinnantila) = List(Hyvaksytty, VarasijaltaHyvaksytty).contains(tila) //TODO entäs täyttyjonosäännöllä hyväksytty?
      def vastaanotto(vastaanotto: ValintatuloksenTila) = vastaanotto != ValintatuloksenTila.KESKEN
      def vastaanottoEiMyohastynyt(vastaanotto: ValintatuloksenTila) = vastaanotto != ValintatuloksenTila.KESKEN && vastaanotto != ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA
      def hylattyTaiVaralla(tila: Valinnantila) = Hylatty == tila || Varalla == tila
      def vastaanottaneena(tila: ValintatuloksenTila) = List(ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI, ValintatuloksenTila.EHDOLLISESTI_VASTAANOTTANUT, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA).contains(tila)
      def peruneena(tila: Valinnantila) = List(Perunut, Peruuntunut, Peruutettu).contains(tila)
      def keskenTaiPerunut(tila: ValintatuloksenTila) = List(ValintatuloksenTila.KESKEN, ValintatuloksenTila.PERUUTETTU, ValintatuloksenTila.PERUNUT, ValintatuloksenTila.EI_VASTAANOTETTU_MAARA_AIKANA).contains(tila)

      (uusi.valinnantila, uusi.vastaanottotila, uusi.ilmoittautumistila) match {
        case (t, _, _) if uusi.ohitaVastaanotto.getOrElse(false) && uusi.ohitaIlmoittautuminen.getOrElse(false) => Right()
        case (t, v, _) if hyvaksytty(t) && keskenTaiPerunut(v) && uusi.ohitaIlmoittautuminen.getOrElse(false) => Right()
        case (t, v, i) if t == Peruuntunut && v == ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN && i == EiTehty => Right()
        case (_, _, _) if uusi.ohitaVastaanotto.getOrElse(false) || uusi.ohitaIlmoittautuminen.getOrElse(false) => Left(ValinnantulosUpdateStatus(409,
          s"Vastaanoton tai ilmoittautumisen tallennusta ei voida ohittaa", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if ilmoittautunut(i) && !(hyvaksytty(t) && vastaanotto(v)) => Left(ValinnantulosUpdateStatus(409,
          s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if hylattyTaiVaralla(t) && (vastaanottaneena(v) || ilmoittautunut(i)) => Left(ValinnantulosUpdateStatus(409,
          s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if peruneena(t) && !keskenTaiPerunut(v) => Left(ValinnantulosUpdateStatus(409,
          s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if ( t == Perunut && v == ValintatuloksenTila.PERUNUT ) || (t == Peruutettu && v == ValintatuloksenTila.PERUUTETTU) => Right()
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

    def validateVastaanottoNotChanged() = vanha.map(_.vastaanottotila) match {
      case Some(uusi.vastaanottotila) => Right()
      case None => Right() //Ohitetaan vastaanoton tarkistus, jos insert
      case Some(x) if uusi.ohitaVastaanotto.getOrElse(false) => Right()
      case Some(x) => Left(ValinnantulosUpdateStatus(409,
        s"Valinnantulosta ei voida päivittää, koska vastaanottoa ${uusi.vastaanottotila} on muutettu samanaikaisesti tilaan ${x}", uusi.valintatapajonoOid, uusi.hakemusOid))
    }

    def validateMuutos() = {
      for {
        poisto <- validatePoisto.right
        tilat <- validateTilat.right
        valinnantila <- validateValinnantila.right
        ehdollinenHyvaksynta <- validateEhdollisestiHyvaksyttavissa.right
        julkaistavissa <- validateJulkaistavissa.right
        ilmoittautuminen <- validateIlmoittautuminen.right
      } yield ilmoittautuminen
    }

    validateMuutos().fold(
      e => DBIO.successful(Left(e)),
      _ => vastaanottoValidator.validateVastaanotto(uusi, vanha)
    )
  }

  def save(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): DBIO[Unit] = {
    val muokkaaja = session.personOid
    val selite = "Erillishaun tallennus"

    def vastaanottoAction() = new VirkailijanVastaanotto(haku.oid, uusi.valintatapajonoOid, uusi.henkiloOid, uusi.hakemusOid, hakukohdeOid,
      VirkailijanVastaanottoAction.getVirkailijanVastaanottoAction(Vastaanottotila.values.find(Vastaanottotila.matches(_, uusi.vastaanottotila))
        .getOrElse(throw new IllegalArgumentException(s"Odottamaton vastaanottotila ${uusi.vastaanottotila}"))), muokkaaja, selite)

    def createInsertOperations = {
      List(
        Some(valinnantulosRepository.storeValinnantila(uusi.getValinnantilanTallennus(muokkaaja), ifUnmodifiedSince)),
        Some(valinnantulosRepository.storeValinnanTilanKuvaus(
          hakukohdeOid,
          uusi.valintatapajonoOid,
          uusi.hakemusOid,
          EiTilankuvauksenTarkennetta,
          uusi.valinnantilanKuvauksenTekstiFI,
          uusi.valinnantilanKuvauksenTekstiSV,
          uusi.valinnantilanKuvauksenTekstiEN
        )),
        Some(valinnantulosRepository.storeValinnantuloksenOhjaus(uusi.getValinnantuloksenOhjaus(muokkaaja, selite), ifUnmodifiedSince)),
        uusi.getEhdollisenHyvaksynnanEhto.map(valinnantulosRepository.insertHyvaksynnanEhtoValintatapajonossa(
          uusi.hakemusOid,
          uusi.valintatapajonoOid,
          uusi.hakukohdeOid,
          _)),
        Option(uusi.ilmoittautumistila != EiTehty).collect { case true => valinnantulosRepository.storeIlmoittautuminen(
          uusi.henkiloOid, Ilmoittautuminen(uusi.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), ifUnmodifiedSince)
        },
        Option(uusi.julkaistavissa.getOrElse(false) && uusi.isHyvaksytty).collect{
          case true => valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(uusi.hakemusOid, uusi.valintatapajonoOid, muokkaaja, selite)
        },
        Option(uusi.vastaanottotila != ValintatuloksenTila.KESKEN && uusi.vastaanottotila != ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN).collect{
          case true => valinnantulosRepository.storeAction(vastaanottoAction())
        }
      ).flatten
    }

    def createUpdateOperations(vanha: Valinnantulos) = {
      List(
        Option(uusi.valinnantila != vanha.valinnantila).collect { case true =>
          valinnantulosRepository.storeValinnantila(uusi.getValinnantilanTallennus(muokkaaja), ifUnmodifiedSince)
        },
        Option(uusi.hasOhjausChanged(vanha)).collect { case true => valinnantulosRepository.storeValinnantuloksenOhjaus(
          uusi.getValinnantuloksenOhjauksenMuutos(vanha, muokkaaja, selite), ifUnmodifiedSince)
        },
        Option((
          uusi.hasEhdollisenHyvaksynnanEhtoChanged(vanha),
          uusi.getEhdollisenHyvaksynnanEhto,
          vanha.getEhdollisenHyvaksynnanEhto.isDefined
        )).collect {
          case (true, None, _) =>
            valinnantulosRepository.deleteHyvaksynnanEhtoValintatapajonossa(
              uusi.hakemusOid, uusi.valintatapajonoOid, uusi.hakukohdeOid, ifUnmodifiedSince.get)
          case (true, Some(ehto), false) =>
            valinnantulosRepository.insertHyvaksynnanEhtoValintatapajonossa(
              uusi.hakemusOid, uusi.valintatapajonoOid, uusi.hakukohdeOid, ehto)
          case (true, Some(ehto), true) =>
            valinnantulosRepository.updateHyvaksynnanEhtoValintatapajonossa(
              uusi.hakemusOid, uusi.valintatapajonoOid, uusi.hakukohdeOid, ehto, ifUnmodifiedSince.get)
        },
        Option(uusi.ilmoittautumistila != vanha.ilmoittautumistila && !uusi.ohitaIlmoittautuminen.getOrElse(false)).collect {
          case true => valinnantulosRepository.storeIlmoittautuminen(
            vanha.henkiloOid, Ilmoittautuminen(vanha.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), ifUnmodifiedSince)
        },
        Option(uusi.julkaistavissa.getOrElse(false) && uusi.isHyvaksytty).collect{
          case true => valinnantulosRepository.setHyvaksyttyJaJulkaistavissa(uusi.hakemusOid, uusi.valintatapajonoOid, muokkaaja, selite)
        },
        Option(vanha.isHyvaksytty && !uusi.isHyvaksytty).collect{
          case true => valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(uusi.henkiloOid, uusi.hakukohdeOid, ifUnmodifiedSince)
        },
        Option(uusi.vastaanottotila != vanha.vastaanottotila &&
          !(uusi.vastaanottotila == ValintatuloksenTila.KESKEN && vanha.vastaanottotila == ValintatuloksenTila.OTTANUT_VASTAAN_TOISEN_PAIKAN)).collect{
          case true => valinnantulosRepository.storeAction(vastaanottoAction())
        },
        Option(uusi.hasValinnantilanKuvauksenTekstiChanged(vanha)).collect {
          case true =>
            valinnantulosRepository.storeValinnanTilanKuvaus(
              hakukohdeOid,
              uusi.valintatapajonoOid,
              uusi.hakemusOid,
              EiTilankuvauksenTarkennetta,
              uusi.valinnantilanKuvauksenTekstiFI,
              uusi.valinnantilanKuvauksenTekstiSV,
              uusi.valinnantilanKuvauksenTekstiEN
            )
        }
      ).flatten
    }

    def createDeleteOperations(vanha:Valinnantulos) = {
      List(
        Option(vanha.ehdollisestiHyvaksyttavissa.contains(true)).collect {
          case true => valinnantulosRepository.deleteHyvaksynnanEhtoValintatapajonossa(
            uusi.hakemusOid, uusi.valintatapajonoOid, uusi.hakukohdeOid, ifUnmodifiedSince.get)
        },
        Some(valinnantulosRepository.deleteValinnantulos(muokkaaja, uusi, ifUnmodifiedSince)),
        Option(vanha.ilmoittautumistila != EiTehty).collect { case true => valinnantulosRepository.deleteIlmoittautuminen(
          uusi.henkiloOid, Ilmoittautuminen(uusi.hakukohdeOid, uusi.ilmoittautumistila, muokkaaja, selite), ifUnmodifiedSince
        )},
        Option(uusi.vastaanottotila == ValintatuloksenTila.KESKEN && vanha.vastaanottotila != ValintatuloksenTila.KESKEN).collect {
          case true => valinnantulosRepository.storeAction(vastaanottoAction())
        },
        Some(valinnantulosRepository.deleteHyvaksyttyJaJulkaistavissaIfExists(uusi.henkiloOid, uusi.hakukohdeOid, ifUnmodifiedSince))
      ).flatten
    }

    val operations = ( uusi.poistettava.getOrElse(false), vanhaOpt ) match {
      case (false, None) => logger.info(s"Käyttäjä $muokkaaja lisäsi " +
        s"hakemukselle ${uusi.hakemusOid} valinnantuloksen erillishaun valintatapajonossa ${uusi.valintatapajonoOid}:" +
        s"vastaanottotila on ${uusi.vastaanottotila} ja " +
        s"valinnantila on ${uusi.valinnantila} ja " +
        s"ilmoittautumistila on ${uusi.ilmoittautumistila}.")
        hakukohdeRecord match {
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

    DBIO.sequence(operations).map(_ => ())
  }

  private def target(uusi: Valinnantulos): Target = {
    new Target.Builder()
      .setField("hakukohde", uusi.hakukohdeOid.toString)
      .setField("valintatapajono", uusi.valintatapajonoOid.toString)
      .setField("hakemus", uusi.hakemusOid.toString)
      .build()
  }

  def audit(uusi: Valinnantulos, vanhaOpt: Option[Valinnantulos]): Unit = (uusi.poistettava.getOrElse(false), vanhaOpt) match {
    case (false, Some(vanha)) =>
      audit.log(auditInfo.user, ValinnantuloksenMuokkaus,
        target(uusi),
        new Changes.Builder()
          .updated("valinnantila", vanha.valinnantila.toString, uusi.valinnantila.toString)
          .updated("ehdollisestiHyvaksyttavissa", vanha.ehdollisestiHyvaksyttavissa.getOrElse(false).toString, uusi.ehdollisestiHyvaksyttavissa.getOrElse(false).toString)
          .updated("julkaistavissa", vanha.julkaistavissa.getOrElse(false).toString, uusi.julkaistavissa.getOrElse(false).toString)
          .updated("hyvaksyttyVarasijalta", vanha.hyvaksyttyVarasijalta.getOrElse(false).toString, uusi.hyvaksyttyVarasijalta.getOrElse(false).toString)
          .updated("hyvaksyPeruuntunut", vanha.hyvaksyPeruuntunut.getOrElse(false).toString, uusi.hyvaksyPeruuntunut.getOrElse(false).toString)
          .updated("vastaanottotila", vanha.vastaanottotila.toString, uusi.vastaanottotila.toString)
          .updated("ilmoittautumistila", vanha.ilmoittautumistila.toString, uusi.ilmoittautumistila.toString)
          .build()
      )
    case (false, None) =>
      audit.log(auditInfo.user, ValinnantuloksenLisays,
        target(uusi),
        new Changes.Builder()
          .added("valinnantila", uusi.valinnantila.toString)
          .added("ehdollisestiHyvaksyttavissa", uusi.ehdollisestiHyvaksyttavissa.getOrElse(false).toString)
          .added("julkaistavissa", uusi.julkaistavissa.getOrElse(false).toString)
          .added("hyvaksyttyVarasijalta", uusi.hyvaksyttyVarasijalta.getOrElse(false).toString)
          .added("hyvaksyPeruuntunut", uusi.hyvaksyPeruuntunut.getOrElse(false).toString)
          .added("vastaanottotila", uusi.vastaanottotila.toString)
          .added("ilmoittautumistila", uusi.ilmoittautumistila.toString)
          .build()
      )
    case (true, Some(vanha)) =>
      audit.log(auditInfo.user, ValinnantuloksenPoisto,
        target(uusi),
        new Changes.Builder()
          .removed("valinnantila", vanha.valinnantila.toString)
          .removed("ehdollisestiHyvaksyttavissa", vanha.ehdollisestiHyvaksyttavissa.getOrElse(false).toString)
          .removed("julkaistavissa", vanha.julkaistavissa.getOrElse(false).toString)
          .removed("hyvaksyttyVarasijalta", vanha.hyvaksyttyVarasijalta.getOrElse(false).toString)
          .removed("hyvaksyPeruuntunut", vanha.hyvaksyPeruuntunut.getOrElse(false).toString)
          .removed("vastaanottotila", vanha.vastaanottotila.toString)
          .removed("ilmoittautumistila", vanha.ilmoittautumistila.toString)
          .build()
      )
    case (true, None) =>
      throw new IllegalStateException("Ei voida poistaa olematonta valinnantulosta")
  }
}
