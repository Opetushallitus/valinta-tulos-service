package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.domain.ValintatuloksenTila
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.valintarekisteri.domain._
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValintarekisteriValintatulosDaoSpec extends ITSpecification with ValintarekisteriTestData {
  step(deleteAll())

  lazy val dao = new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb)

  step(createHakujen1Ja2ValinnantuloksetIlmanSijoittelua)

  "load valintatulokset for haku" in {
    val hakukohdeOids = dao.loadValintatulokset(hakuOid1).map(_.getHakemusOid)
    hakukohdeOids.size must_== 4
    List("hakemus1", "hakemus2", "hakemus3", "hakemus4").diff(hakukohdeOids) must_== List()
  }

  "load valintatulokset for hakukohde" in {
    val hakukohdeOids = dao.loadValintatuloksetForHakukohde(oidHaku2hakukohde1).map(_.getHakemusOid)
    hakukohdeOids.size must_== 4
    List("hakemus5", "hakemus6", "hakemus7", "hakemus8").diff(hakukohdeOids) must_== List()
  }

  "load valintatulokset for hakemus" in {
    val valintatulokset1 = dao.loadValintatuloksetForHakemus(hakemusOid6)
    valintatulokset1.size must_== 1
    valintatulokset1.head.getHakemusOid must_== hakemusOid6.toString

    insertValinnantulos(
      hakuOid2,
      valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, hakemusOid6)
    )

    val valintatulokset2 = dao.loadValintatuloksetForHakemus(hakemusOid6)
    valintatulokset2.size must_== 2
    valintatulokset2.map(_.getHakemusOid).distinct.diff(List(hakemusOid6.toString)) must_== List()
    valintatulokset2
      .map(_.getTila)
      .diff(
        List(
          ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI,
          ValintatuloksenTila.VASTAANOTTANUT_SITOVASTI
        )
      ) must_== List()
    valintatulokset2
      .map(vt => ValintatapajonoOid(vt.getValintatapajonoOid))
      .diff(List(oidHaku2hakukohde1jono1, oidHaku2hakukohde1jono2)) must_== List()
  }

  step(deleteAll())
}
