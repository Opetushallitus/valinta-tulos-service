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

//vanha muoto
//case class Organisaatio(oid: String, nimi: Map[String, String], oppilaitosKoodi: Option[String], organisaatiotyypit: List[String], children: Seq[Organisaatio])

case class Organisaatio(oid: String, nimi: Map[String, String], tyypit: List[String], oppilaitosKoodi: Option[String], children: Seq[Organisaatio] = Seq()) {
  def isOppilaitos: Boolean = tyypit.exists(tyyppi => tyyppi.startsWith("organisaatiotyyppi_02"))
  def find(pred: Organisaatio => Boolean): Option[Organisaatio] = if (pred(this)) Some(this) else children.find(pred)
}
