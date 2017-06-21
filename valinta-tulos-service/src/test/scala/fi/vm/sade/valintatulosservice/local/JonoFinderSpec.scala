package fi.vm.sade.valintatulosservice.local

import fi.vm.sade.sijoittelu.tulos.dto.HakemuksenTila
import fi.vm.sade.sijoittelu.tulos.dto.raportointi.{HakutoiveDTO, HakutoiveenValintatapajonoDTO}
import fi.vm.sade.valintatulosservice.sijoittelu.JonoFinder
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.collection.JavaConversions._

@RunWith(classOf[JUnitRunner])
class JonoFinderSpec extends Specification {


  "JonoFinder" should {

    "handle case no 'jonos'" in {
      JonoFinder.merkitseväJono(new HakutoiveDTO()) must_== None
    }
    "handle case one 'jono'" in {
      val hakutoive = new HakutoiveDTO()
      val jono1 = jonoWithTila(HakemuksenTila.HARKINNANVARAISESTI_HYVAKSYTTY, None)
      hakutoive.setHakutoiveenValintatapajonot(List(jono1))
      JonoFinder.merkitseväJono(hakutoive) must_== Some(jono1)
    }
    "head 'jono' should be last possible 'jono' with same priority" in {

      val hakutoive = new HakutoiveDTO()

      val jono1 = jonoWithTila(HakemuksenTila.HYLATTY, None)
      jono1.setTilanKuvaukset(Map("FI" -> "EKA"))
      val jono2 = jonoWithTila(HakemuksenTila.HYLATTY, None)
      jono2.setTilanKuvaukset(Map("FI" -> "TOKA"))

      hakutoive.setHakutoiveenValintatapajonot(List(jono1, jono2))

      val outJono = JonoFinder.merkitseväJono(hakutoive)

      outJono.get.getTilanKuvaukset.get("FI") must_== "TOKA"

    }

    "when 'varalla' use jono with smallest 'varasija numero'" in {
      val hakutoive = new HakutoiveDTO()

      val jono1 = jonoWithTila(HakemuksenTila.VARALLA, Some(10))
      val jono2 = jonoWithTila(HakemuksenTila.VARALLA, Some(5))
      val jono3 = jonoWithTila(HakemuksenTila.VARALLA, Some(7))

      hakutoive.setHakutoiveenValintatapajonot(List(jono1, jono2, jono3))

      JonoFinder.merkitseväJono(hakutoive) must_== Some(jono2)

    }
  }

  private def jonoWithTila(tila: HakemuksenTila,
                           varasijaNumero: Option[Integer],
                           prioriteetti: Int = 0): HakutoiveenValintatapajonoDTO = {
    
    val jono = new HakutoiveenValintatapajonoDTO()
    jono.setTila(tila)
    jono.setVarasijanNumero(varasijaNumero.getOrElse(0))
    jono.setValintatapajonoPrioriteetti(prioriteetti)
    jono
  }
}
