package fi.vm.sade.utils.tarjonta

import fi.vm.sade.valintatulosservice.tarjonta.{Komo, Koodi, Koulutus}

trait TarjontaBuilder {

  def koulutus = KoulutusBuilder()
  def komo = KomoBuilder()
}

case class KomoBuilder(komo: Komo = Komo("oid",None, None)) {
  def withKoulutuskoodi(arvo: String) = this.copy(komo = komo.copy(koulutuskoodi = Some(Koodi("koulutus_"+arvo, arvo))))
  def withOpintojenLaajuusarvo(arvo: String) = this.copy(komo = komo.copy(opintojenLaajuusarvo = Some(Koodi("opintojenlaajuus_"+ arvo,arvo))))
}
case class KoulutusBuilder(koulutus: Koulutus = Koulutus("oid",null,"",true, Seq(), Seq(), None, None, None)) {

  def withSisaltyvatKoulutuskoodit(k: Seq[String]) = KoulutusBuilder(koulutus.copy(sisaltyvatKoulutuskoodit = k))
  def withChildren(k: Seq[String]) = KoulutusBuilder(koulutus.copy(children = k))
  def withKoulutuskoodi(arvo: String) = KoulutusBuilder(koulutus.copy(koulutuskoodi = Some(Koodi("koulutus_" + arvo, arvo))))
  def withOpintojenLaajuusarvo(arvo: String) = KoulutusBuilder(koulutus.copy(opintojenLaajuusarvo = Some(Koodi("opintojenlaajuus_"+ arvo,arvo))))

}