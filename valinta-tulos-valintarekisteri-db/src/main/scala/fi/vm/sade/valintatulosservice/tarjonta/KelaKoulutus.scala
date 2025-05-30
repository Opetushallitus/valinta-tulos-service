package fi.vm.sade.valintatulosservice.tarjonta

import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Kausi, Syksy}

import scala.collection.immutable
import scala.util.Try

case class KelaKoulutus(val tutkinnontaso: Option[String],val tutkinnonlaajuus1: Option[String], val tutkinnonlaajuus2: Option[String]) extends KelaLaajuus with KelaTutkinnontaso

trait KelaLaajuus {
  val tutkinnonlaajuus1: Option[String]
  val tutkinnonlaajuus2: Option[String]
}
trait KelaTutkinnontaso {
  val tutkinnontaso: Option[String]
}
private object MuuTutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = None, tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object OpistovuosiTutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("008"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object TuvaTutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("007"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object TelmaKoulutus {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("006"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object ValmaKoulutus {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("005"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object Erikoisammattitutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("004"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object Ammattitutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("003"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object AmmatillinenPerustutkinto {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("002"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object LukioKoulutus {
  def apply(laajuus: Option[String]) = {
    KelaKoulutus(tutkinnontaso = Some("001"), tutkinnonlaajuus1 = None, tutkinnonlaajuus2 = None)
  }
}
private object AlempiKKTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    if(laajuus2.isDefined) {
      KelaKoulutus(tutkinnontaso = Some("050"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = None)
    } else {
      KelaKoulutus(tutkinnontaso = Some("050"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = None)
    }
  }
}
private object AlempiYlempiKKTutkinto {
  def apply(alempi: Alempi, ylempi: Ylempi) = {

    if(alempi.laajuusarvo.isDefined && !alempi.isCombinedLaajuusarvo && !ylempi.isCombinedLaajuusarvo) {
      KelaKoulutus(tutkinnontaso = Some("060"), tutkinnonlaajuus1 = alempi.laajuusarvo, tutkinnonlaajuus2 = ylempi.laajuusarvo)
    } else {
      val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(KelaKoulutus.mergeLaajuudet(alempi,ylempi).laajuusarvo)
      KelaKoulutus(tutkinnontaso = Some("060"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
    }
  }
}
private object ErillinenYlempiKKTutkinto {
  def apply(laajuus: Option[String]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    if(laajuus2.isDefined) {
      KelaKoulutus(tutkinnontaso = Some("061"), tutkinnonlaajuus1 = laajuus2, tutkinnonlaajuus2 = None)
    } else {
      KelaKoulutus(tutkinnontaso = Some("061"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = None)
    }
  }
}
private object LääketieteenLisensiaatti {
  def apply(laajuus: Option[String], koulutuksenAlkamiskausi: Option[Kausi]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    // 1.8.2025 tai sen jälkeen alkaneiden lääketieteen lisensiaatin ja hammaslääketieteen lisensiaatin tasokoodiksi 060 (alempi + ylempi)
    if (koulutuksenAlkamiskausi.isDefined &&
        (koulutuksenAlkamiskausi.get == Syksy(2025) || koulutuksenAlkamiskausi.get.year > 2025)) {
      KelaKoulutus(tutkinnontaso = Some("060"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
    } else {
      KelaKoulutus(tutkinnontaso = Some("070"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
    }
  }
}
private object HammaslääketieteenLisensiaatti {
  def apply(laajuus: Option[String], koulutuksenAlkamiskausi: Option[Kausi]) = {
    val (laajuus1, laajuus2) = KelaKoulutus.asLaajuus1AndLaajuus2(laajuus)
    // 1.8.2025 tai sen jälkeen alkaneiden lääketieteen lisensiaatin ja hammaslääketieteen lisensiaatin tasokoodiksi 060 (alempi + ylempi)
    if (koulutuksenAlkamiskausi.isDefined &&
        (koulutuksenAlkamiskausi.get == Syksy(2025) || koulutuksenAlkamiskausi.get.year > 2025)) {
      KelaKoulutus(tutkinnontaso = Some("060"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
    } else {
      KelaKoulutus(tutkinnontaso = Some("071"), tutkinnonlaajuus1 = laajuus1, tutkinnonlaajuus2 = laajuus2)
    }
  }
}
private trait Taso {
  val laajuusarvo: Option[String]
  val isCombinedLaajuusarvo = laajuusarvo.exists(_.contains("+"))
}
private case class Lääkis(val laajuusarvo: Option[String]) extends Taso
private case class Hammas(val laajuusarvo: Option[String]) extends Taso
private case class Alempi(val laajuusarvo: Option[String]) extends Taso
private case class Ylempi(val laajuusarvo: Option[String]) extends Taso
private case class Telma(val laajuusarvo: Option[String]) extends Taso
private case class Valma(val laajuusarvo: Option[String]) extends Taso
private case class Erikoisammatti(val laajuusarvo: Option[String]) extends Taso
private case class Ammatti(val laajuusarvo: Option[String]) extends Taso
private case class AmmatillinenPerus(val laajuusarvo: Option[String]) extends Taso
private case class Lukio(val laajuusarvo: Option[String]) extends Taso
private case class Tuva(val laajuusarvo: Option[String]) extends Taso
private case class Opistovuosi(val laajuusarvo: Option[String]) extends Taso
private case class Muu(val laajuusarvo: Option[String]) extends Taso

object KelaKoulutus extends Logging {
  def mergeLaajuudet[A <: Taso](l1: A, l2: A): A = {
    l1.laajuusarvo match {
      case Some(laajuus1) =>
        l2.laajuusarvo match {
          case Some(laajuus2) =>
            if (laajuus1 == laajuus2 || laajuus1.contains(laajuus2)) {
              l1
            } else if (laajuus2.contains(laajuus1)) {
              l2
            } else {
              throw new RuntimeException(s"Unable to solve laajuusarvo conflict between ${l1} and ${l2}!")
            }
          case None => l1
        }
      case None => l2
    }
  }

  def asLaajuus1AndLaajuus2(laajuus: Option[String]): (Option[String], Option[String]) = {
    val l = laajuus.map(_.split("\\+").toList).getOrElse(List())
      .filter(_.forall(_.isDigit))
      .filter(_.length < 4)
      .filter(_.length > 0)
      .map(l0 => "%03d".format(l0.toInt))

    l match {
      case List(l1, l2) => (Some(l1), Some(l2))
      case List(l1) => (Some(l1), None)
      case List() => (Some(""), None)
      case _ => throw new RuntimeException("Unexpected laajuus format: " + laajuus)
    }
  }

  def apply(ks: Seq[KoulutusLaajuusarvo], koulutuksenAlkamiskausi: Option[Kausi]): Option[KelaKoulutus] = {
    val tasot = ks.flatMap(toTaso)

    val (alemmat, ylemmät, lääkis, hammas, telma, valma, eat, at, apt, lukio, muut, tuva, opistovuosi) = separate(tasot)

    def mergeMuut(muut: List[Muu]): Option[Muu] = {
      val laajuusarvot = muut.flatMap(_.laajuusarvo).toSet
      if (laajuusarvot.isEmpty) {
        None
      } else {
        Some(Muu(laajuusarvo = Some(laajuusarvot.mkString("+"))))
      }
    }

    val reduced: (Option[Alempi], Option[Ylempi], Option[Lääkis], Option[Hammas], Option[Telma], Option[Valma], Option[Erikoisammatti], Option[Ammatti], Option[AmmatillinenPerus], Option[Lukio], Option[Muu], Option[Tuva], Option[Opistovuosi]) =
      (alemmat.reduceOption(mergeLaajuudet[Alempi]),
        ylemmät.reduceOption(mergeLaajuudet[Ylempi]),
        lääkis.reduceOption(mergeLaajuudet[Lääkis]),
        hammas.reduceOption(mergeLaajuudet[Hammas]),
        telma.reduceOption(mergeLaajuudet[Telma]),
        valma.reduceOption(mergeLaajuudet[Valma]),
        eat.reduceOption(mergeLaajuudet[Erikoisammatti]),
        at.reduceOption(mergeLaajuudet[Ammatti]),
        apt.reduceOption(mergeLaajuudet[AmmatillinenPerus]),
        lukio.reduceOption(mergeLaajuudet[Lukio]),
        mergeMuut(muut),
        tuva.reduceOption(mergeLaajuudet[Tuva]),
        opistovuosi.reduceOption(mergeLaajuudet[Opistovuosi]))

    reduced match {
      case (None, Some(Ylempi(laajuus)), None, None, None, None, None, None, None, None, _, None, None) => Some(ErillinenYlempiKKTutkinto(laajuus))
      case (Some(alempi), Some(ylempi), None, None, None, None, None, None, None, None, _, None, None) => Some(AlempiYlempiKKTutkinto(alempi, ylempi))
      case (Some(Alempi(laajuus)), None, None, None, None, None, None, None, None, None, _, None, None) => Some(AlempiKKTutkinto(laajuus))
      case (None, None, Some(Lääkis(laajuus)), None, None, None, None, None, None, None, _, None, None) => Some(LääketieteenLisensiaatti(laajuus, koulutuksenAlkamiskausi))
      case (None, None, None, Some(Hammas(laajuus)), None, None, None, None, None, None, _, None, None) => Some(HammaslääketieteenLisensiaatti(laajuus, koulutuksenAlkamiskausi))
      case (None, None, None, None, Some(Telma(laajuus)), None, None, None, None, None, _, None, None) => Some(TelmaKoulutus(laajuus))
      case (None, None, None, None, None, Some(Valma(laajuus)), None, None, None, None, _, None, None) => Some(ValmaKoulutus(laajuus))
      case (None, None, None, None, None, None, Some(Erikoisammatti(laajuus)), None, None, None, _, None, None) => Some(Erikoisammattitutkinto(laajuus))
      case (None, None, None, None, None, None, None, Some(Ammatti(laajuus)), None, None, _, None, None) => Some(Ammattitutkinto(laajuus))
      case (None, None, None, None, None, None, None, None, Some(AmmatillinenPerus(laajuus)), None, _, None, None) => Some(AmmatillinenPerustutkinto(laajuus))
      case (None, None, None, None, None, None, None, None, None, Some(Lukio(laajuus)), _, None, None) => Some(LukioKoulutus(laajuus))
      case (None, None, None, None, None, None, None, None, None, None, Some(Muu(laajuus)), None, None) => Some(MuuTutkinto(laajuus))
      case (None, None, None, None, None, None, None, None, None, None, _, Some(Tuva(laajuus)), None) => Some(TuvaTutkinto(laajuus))
      case (None, None, None, None, None, None, None, None, None, None, _, None, Some(Opistovuosi(laajuus))) => Some(OpistovuosiTutkinto(laajuus))
      case _ =>
        logger.warn(s"$reduced ei ole Kela-koulutus")
        None
    }

  }

  private def separate(tasot: Seq[Taso]): (List[Alempi], List[Ylempi], List[Lääkis], List[Hammas], List[Telma], List[Valma], List[Erikoisammatti], List[Ammatti], List[AmmatillinenPerus], List[Lukio], List[Muu], List[Tuva], List[Opistovuosi]) = {
    val empty: (List[Alempi], List[Ylempi], List[Lääkis], List[Hammas], List[Telma], List[Valma], List[Erikoisammatti], List[Ammatti], List[AmmatillinenPerus], List[Lukio], List[Muu], List[Tuva], List[Opistovuosi]) = (Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil, Nil)
    tasot.foldRight(empty) { case (f, (as, bs, cs, ds, es, fs, gs, hs, is, js, ks, ls, ms)) =>
      f match {
        case a@Alempi(_) => (a :: as, bs, cs, ds, es, fs, gs, hs, is, js, ks, ls, ms)
        case b@Ylempi(_) => (as, b :: bs, cs, ds, es, fs, gs, hs, is, js, ks, ls, ms)
        case c@Lääkis(_) => (as, bs, c :: cs, ds, es, fs, gs, hs, is, js, ks, ls, ms)
        case d@Hammas(_) => (as, bs, cs, d :: ds, es, fs, gs, hs, is, js, ks, ls, ms)
        case e@Telma(_) => (as, bs, cs, ds, e :: es, fs, gs, hs, is, js, ks, ls, ms)
        case f@Valma(_) => (as, bs, cs, ds, es, f :: fs, gs, hs, is, js, ks, ls, ms)
        case g@Erikoisammatti(_) => (as, bs, cs, ds, es, fs, g :: gs, hs, is, js, ks, ls, ms)
        case h@Ammatti(_) => (as, bs, cs, ds, es, fs, gs, h :: hs, is, js, ks, ls, ms)
        case i@AmmatillinenPerus(_) => (as, bs, cs, ds, es, fs, gs, hs, i :: is, js, ks, ls, ms)
        case j@Lukio(_) => (as, bs, cs, ds, es, fs, gs, hs, is, j :: js, ks, ls, ms)
        case k@Muu(_) => (as, bs, cs, ds, es, fs, gs, hs, is, js, k :: ks, ls, ms)
        case l@Tuva(_) => (as, bs, cs, ds, es, fs, gs, hs, is, js, ks, l :: ls, ms)
        case m@Opistovuosi(_) => (as, bs, cs, ds, es, fs, gs, hs, is, js, ks, ls, m :: ms)
      }
    }
  }

  private def toTaso(k: KoulutusLaajuusarvo): Option[Taso] = {
    implicit class Regex(sc: StringContext) {
      def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
    }
    val arvo = k.opintojenLaajuusarvo.filter(_.trim.nonEmpty)
    val result: Option[Taso] = k.koulutuskoodi match {
      case Some(koodi) =>
        koodi match {
//          case r"999903" =>
//            Some(Telma(laajuusarvo = arvo))
          case r"999908" =>
            Some(Tuva(laajuusarvo = arvo))
          case r"999909" =>
            Some(Opistovuosi(laajuusarvo = arvo))
          case r"772101" =>
            Some(Lääkis(laajuusarvo = arvo))
          case r"772201" =>
            Some(Hammas(laajuusarvo = arvo))
          case r"6.*" =>
            Some(Alempi(laajuusarvo = arvo))
          case r"7.*" =>
            Some(Ylempi(laajuusarvo = arvo))
          case _ =>
            resolveToinenAsteOrMuu(k.koulutustyyppi, arvo)
        }
      case _ =>
        None
    }
    logger.info("Koulutuskoodi {} and koulutustyyppi {} resulted in taso {}", k.koulutuskoodi, k.koulutustyyppi, result.map(_.getClass.getSimpleName))
    result
  }

  private def resolveToinenAsteOrMuu(koulutustyyppi: Option[String], arvo: Option[String]): Option[Taso] = {
    koulutustyyppi match {
      case Some(koodi) =>
        koodi match {
          case "1" | "4" | "13" | "26" =>
            Some(AmmatillinenPerus(laajuusarvo = arvo))
          case "2" | "14" | "21" =>
            Some(Lukio(laajuusarvo = arvo))
          case "5" =>
            Some(Telma(laajuusarvo = arvo))
          case "11" =>
            Some(Ammatti(laajuusarvo = arvo))
          case "12" =>
            Some(Erikoisammatti(laajuusarvo = arvo))
          case "18" | "19" =>
            Some(Valma(laajuusarvo = arvo))
          case _ =>
            Some(Muu(laajuusarvo = arvo))
        }
      case None =>
        Some(Muu(laajuusarvo = arvo))
    }
  }

}

case class KoulutusLaajuusarvo(oid: Option[String], koulutuskoodi: Option[String], koulutustyyppi: Option[String], opintojenLaajuusarvo: Option[String])
