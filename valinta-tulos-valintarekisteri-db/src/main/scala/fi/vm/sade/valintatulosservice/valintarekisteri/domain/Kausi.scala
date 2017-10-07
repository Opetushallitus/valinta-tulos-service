package fi.vm.sade.valintatulosservice.valintarekisteri.domain

sealed trait Kausi {
  def year: Int
  def toKausiSpec: String
}

case class Kevat(year: Int) extends Kausi {
  def toKausiSpec: String = year.toString + "K"
}
case class Syksy(year: Int) extends Kausi {
  def toKausiSpec: String = year.toString + "S"
}

object Kausi {
  private val kausi = """(\d\d\d\d)(K|S)""".r

  def apply(kausiSpec: String): Kausi = kausiSpec match {
    case kausi(year, "K") => Kevat(Integer.parseInt(year))
    case kausi(year, "S") => Syksy(Integer.parseInt(year))
    case kausi(year, c) => throw new IllegalArgumentException(s"Illegal last character '$c'. Valid values are 'K' and 'S'")
    case s => throw new IllegalArgumentException(s"Illegal kausi specification '$s'. Expected format YYYY(K|S), e.g. 2015K")
  }
}
