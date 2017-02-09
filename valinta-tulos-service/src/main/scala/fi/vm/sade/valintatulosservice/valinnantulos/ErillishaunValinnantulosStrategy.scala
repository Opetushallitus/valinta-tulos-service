package fi.vm.sade.valintatulosservice.valinnantulos

import java.time.Instant

import fi.vm.sade.valintatulosservice.{AuditInfo, ValinnantulosUpdateStatus}
import fi.vm.sade.valintatulosservice.security.Session
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._

trait ErillishaunValinnantulosStrategy extends ValinnantulosStrategy {

  def handleErillishaunValinnantulokset(auditInfo:AuditInfo, valintatapajonoOid:String, valinnantulokset:List[Valinnantulos], ifUnmodifiedSince: Instant) =
    new ErillishaunValinnantulosContext(auditInfo, valintatapajonoOid, valinnantulokset, ifUnmodifiedSince).handle()

  class ErillishaunValinnantulosContext(val auditInfo:AuditInfo, val valintatapajonoOid:String, val valinnantulokset:List[Valinnantulos], val ifUnmodifiedSince: Instant) extends ValinnantulosContext {
    val session = auditInfo.session._2
    val hakukohdeOid = valinnantulokset.head.hakukohdeOid
    val vanhatValinnantulokset = getVanhatValinnantulokset()

    val (hakuOid:String, tarjoajaOid:String) = getOidsFromTarjonta()

    def getOidsFromTarjonta() = hakuService.getHakukohde(hakukohdeOid).right.toOption.map(hakukohde =>
      (hakukohde.hakuOid, hakukohde.tarjoajaOids.headOption.getOrElse(
        throw new RuntimeException(s"Hakukohteelle ${hakukohdeOid} ei löydy tarjoajaOidia")
      ))
    ).getOrElse(throw new RuntimeException(s"Hakukohdetta ${hakukohdeOid} ei löydy tarjonnasta"))

    def save(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = Right()

    def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {
      def hyvaksytty(tila: Valinnantila) = List(Hyvaksytty, HyvaksyttyVarasijalta).contains(tila)

      //TODO entäs täyttyjonosäännöllä hyväksytty?
      def keskenTaiPerunut(tila: VastaanottoAction) = List(Poista, Peruuta, Peru, MerkitseMyohastyneeksi).contains(tila)

      def vastaanottanutTaiPerunutTaiMyohastynyt(tila: VastaanottoAction) = List(VastaanotaSitovasti, VastaanotaEhdollisesti, MerkitseMyohastyneeksi, Peru, Peruuta).contains(tila)

      def onIlmoittautunut(ilmoittautuminen: SijoitteluajonIlmoittautumistila) = ilmoittautuminen != EiTehty

      def vastaanottanutTaiPerunut(tila: VastaanottoAction) = List(VastaanotaSitovasti, VastaanotaEhdollisesti, MerkitseMyohastyneeksi, Peru, Peruuta).contains(tila)

      def hylattyTaiVaralla(tila: Valinnantila) = Hylatty == tila || Varalla == tila

      def vastaanottaneena(tila: VastaanottoAction) = List(VastaanotaSitovasti, VastaanotaEhdollisesti, MerkitseMyohastyneeksi).contains(tila)

      def peruneena(tila: Valinnantila) = List(Perunut, Peruuntunut, Peruutettu).contains(tila)

      (uusi.valinnantila, uusi.vastaanottotila, uusi.ilmoittautumistila) match {
        case (t, v, EiTehty) if hyvaksytty(t) && keskenTaiPerunut(v) => Right()
        case (t, v, i) if onIlmoittautunut(i) && hyvaksytty(t) && vastaanottanutTaiPerunutTaiMyohastynyt(v) => Right()
        case (t, v, i) if onIlmoittautunut(i) => Left(ValinnantulosUpdateStatus(409,
          s"Ilmoittautumistieto voi olla ainoastaan hyväksytyillä ja vastaanottaneilla hakijoilla", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (Perunut, Peru, _) => Right()
        case (Peruutettu, Peruuta, _) => Right()
        case (t, v, _) if hyvaksytty(t) && vastaanottanutTaiPerunut(v) => Right()
        case (t, v, _) if !hyvaksytty(t) && vastaanottanutTaiPerunut(v) => Left(ValinnantulosUpdateStatus(409,
          s"Vastaanottaneen tai peruneen hakijan tulisi olla hyväksyttynä", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if hylattyTaiVaralla(t) && (!vastaanottaneena(v) && i == EiTehty) => Right()
        case (t, v, i) if hylattyTaiVaralla(t) && !(!vastaanottaneena(v) && i == EiTehty) => Left(ValinnantulosUpdateStatus(409,
          s"Hylätty tai varalla oleva hakija ei voi olla ilmoittautunut tai vastaanottanut", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (t, v, i) if peruneena(t) && keskenTaiPerunut(v) => Right()
        case (t, v, i) if peruneena(t) && !keskenTaiPerunut(v) => Left(ValinnantulosUpdateStatus(409,
          s"Peruneella vastaanottajalla ei voi olla vastaanottotilaa", uusi.valintatapajonoOid, uusi.hakemusOid))
        case (_, _, _) => Right()
      }
    }
  }
}
