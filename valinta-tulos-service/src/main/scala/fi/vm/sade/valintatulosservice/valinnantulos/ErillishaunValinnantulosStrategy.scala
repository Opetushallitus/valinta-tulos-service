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

    val (hakuOid: String, tarjoajaOid: String) = getOidsFromTarjonta()

    def getOidsFromTarjonta() = hakuService.getHakukohde(hakukohdeOid).right.toOption.map(hakukohde =>
      (hakukohde.hakuOid, hakukohde.tarjoajaOids.headOption.getOrElse(
        throw new RuntimeException(s"Hakukohteelle ${hakukohdeOid} ei löydy tarjoajaOidia")
      ))
    ).getOrElse(throw new RuntimeException(s"Hakukohdetta ${hakukohdeOid} ei löydy tarjonnasta"))

    def save(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = Right()

    def validate(uusi: Valinnantulos, vanha: Option[Valinnantulos]) = {
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
  }
}
