package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.valintatulosservice.tarjonta.KelaKoulutus.{Hammas, Ylempi, Alempi, Lääkis}

case class KelaKoulutus(val tutkinnontaso: Option[String],val tutkinnonlaajuus1: String, val tutkinnonlaajuus2: Option[String]) extends KelaLaajuus with KelaTutkinnontaso

trait KelaLaajuus {
  val tutkinnonlaajuus1: String
  val tutkinnonlaajuus2: Option[String]
}
trait KelaTutkinnontaso {
  val tutkinnontaso: Option[String]
}
private object MuuTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = None, tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}
private object AlempiKKTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = Some("050"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}
private object AlempiYlempiKKTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = Some("060"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}
private object ErillinenYlempiKKTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = Some("061"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}
private object LääketieteenLisensiaatti {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = Some("070"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}
private object HammaslääketieteenLisensiaatti {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    KelaKoulutus(tutkinnontaso = Some("071"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
  }
}

object KelaKoulutus {

   def asLaajuus1AndLaajuus2(laajuus: Option[String]): (String,Option[String]) = {
     val l = laajuus.map(_.split("\\+").toList).getOrElse(List())
     l match {
       case List(l1,l2) => (l1, Some(l2))
       case List(l1) => (l1, None)
       case List() => ("", None)
       case _ => throw new RuntimeException("Unexpected laajuus format: " + laajuus)
     }
  }

  private trait Taso {
    val laajuusarvo: Option[String]
  }
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
      case Ylempi(laajuus) :: Nil => Some(ErillinenYlempiKKTutkinto(laajuus))
      case Alempi(laajuus1) :: Ylempi(laajuus2) :: Nil =>
        Some(AlempiYlempiKKTutkinto(laajuus2.map(l2 => laajuus1.map(_ + "+" + l2).getOrElse(l2)).orElse(laajuus1)))
      case Alempi(laajuus) :: Nil => Some(AlempiKKTutkinto(laajuus))
      case Lääkis(laajuus) :: _ => Some(LääketieteenLisensiaatti(laajuus))
      case Hammas(laajuus) :: _ => Some(HammaslääketieteenLisensiaatti(laajuus))
      case Muu(laajuus) :: _ => Some(MuuTutkinto(laajuus))
      case _ =>
        None
    }

  }
  private def toTaso(k: Koulutus): Option[Taso] = {
    implicit class Regex(sc: StringContext) {
      def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
    }
    val arvo = k.opintojenLaajuusarvo.map(_.arvo).filter(_.trim.nonEmpty)
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
