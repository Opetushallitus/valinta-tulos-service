package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.valintatulosservice.tarjonta.KelaKoulutus.{Hammas, Ylempi, Alempi, Lääkis}

trait KelaKoulutus extends KelaLaajuus with KelaTutkinnontaso

trait KelaLaajuus {
  val tutkinnonlaajuus1: String
  val tutkinnonlaajuus2: Option[String]
}
trait KelaTutkinnontaso {
  val tutkinnontaso: Option[String]
}
private case class MuuTutkinto(val tutkinnontaso: Option[String] = None, val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus
private case class AlempiKKTutkinto(val tutkinnontaso: Option[String] = Some("050"), val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus
private case class AlempiYlempiKKTutkinto(val tutkinnontaso: Option[String] = Some("060"), val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus
private case class ErillinenYlempiKKTutkinto(val tutkinnontaso: Option[String] = Some("061"), val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus
private case class LääketieteenLisensiaatti(val tutkinnontaso: Option[String] = Some("070"), val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus
private case class HammaslääketieteenLisensiaatti(val tutkinnontaso: Option[String] = Some("071"), val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String] = None) extends KelaTutkinnontaso with KelaLaajuus

object KelaKoulutus {

  private trait Taso
  private case class Lääkis(val laajuusarvo: Option[String]) extends Taso
  private case class Hammas(val laajuusarvo: Option[String]) extends Taso
  private case class Alempi(val laajuusarvo: Option[String]) extends Taso
  private case class Ylempi(val laajuusarvo: Option[String]) extends Taso
  private case class Muu(val laajuusarvo: Option[String]) extends Taso

  def apply(ks: Seq[Koulutus]): Option[KelaKoulutus] = {
    ks.flatMap(toTaso).sortBy {
      case c: Lääkis => 1
      case d: Hammas => 2
      case a: Alempi => 3
      case b: Ylempi => 4
      case m: Muu => 5
    } match {
      case Ylempi(laajuus) :: Nil =>
        ErillinenYlempiKKTutkinto(tutkinnonlaajuus1 = laajuus.getOrElse(""))
      case Alempi(laajuus1) :: Ylempi(laajuus2) :: Nil =>
        AlempiYlempiKKTutkinto(tutkinnonlaajuus1 = laajuus1.getOrElse(""), tutkinnonlaajuus2 = laajuus2)
      case Alempi(laajuus) :: Nil =>
        AlempiKKTutkinto(tutkinnonlaajuus1 = laajuus.getOrElse(""))
      case Lääkis(laajuus) :: _ =>
        LääketieteenLisensiaatti(tutkinnonlaajuus1 = laajuus.getOrElse(""))
      case Hammas(laajuus) :: _ =>
        HammaslääketieteenLisensiaatti(tutkinnonlaajuus1 = laajuus.getOrElse(""))
      case Muu(laajuus) :: _ =>
        MuuTutkinto(tutkinnonlaajuus1 = laajuus.getOrElse(""))
      case _ =>
        None
    }
    None
  }
  private def toTaso(k: Koulutus): Option[Taso] = {
    implicit class Regex(sc: StringContext) {
      def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
    }
    val arvo = k.opintojenLaajuusarvo.map(_.arvo)
    k.koulutuskoodi match {
      case Some(koodi) =>
        koodi.arvo match {
          case r"772101" =>
            Some(Lääkis(laajuusarvo = arvo))
          case r"772201" =>
            Some(Hammas(laajuusarvo = arvo))
          case r"6.*" =>
            Some(Alempi(laajuusarvo = arvo))
          case r"7.*" =>
            Some(Ylempi(laajuusarvo = arvo))
          case _ =>
            Some(Muu(laajuusarvo = arvo))
        }
      case _ =>
        None
    }
  }

}
