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
}
