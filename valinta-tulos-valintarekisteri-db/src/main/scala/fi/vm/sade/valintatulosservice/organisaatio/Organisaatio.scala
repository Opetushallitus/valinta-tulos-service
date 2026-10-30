package fi.vm.sade.valintatulosservice.organisaatio

import scala.annotation.tailrec

case class Organisaatiot(organisaatiot: Seq[Organisaatio]) {
  def find(pred: Organisaatio => Boolean): Option[Organisaatio] = find(organisaatiot.toList, pred)

  @tailrec
  private def find(organisaatiot: List[Organisaatio], pred: Organisaatio => Boolean): Option[Organisaatio] = organisaatiot.headOption match {
    case Some(organisaatio) if pred(organisaatio) => Some(organisaatio)
    case Some(organisaatio) => find(organisaatiot.tail ++ organisaatio.children, pred)
    case None => None
  }
}

case class Organisaatio(oid: String, nimi: Map[String, String], oppilaitosKoodi: Option[String], organisaatiotyypit: List[String], status: String, children: Seq[Organisaatio]) {
  def isAktiivinenOppilaitos: Boolean = "AKTIIVINEN".equals(status) && organisaatiotyypit.exists(tyyppi => tyyppi.startsWith("organisaatiotyyppi_02"))

  /**
   * Tämän organisaation ja sen kaikkien aliorganisaatioiden oidit kaikilta tasoilta.
   */
  def kaikkiOidit: Set[String] = Organisaatio.kaikkiOidit(List(this), Set.empty)
}

object Organisaatio {
  @tailrec
  private def kaikkiOidit(organisaatiot: List[Organisaatio], acc: Set[String]): Set[String] = organisaatiot match {
    case organisaatio :: tail => kaikkiOidit(tail ++ organisaatio.children, acc + organisaatio.oid)
    case Nil => acc
  }
}
