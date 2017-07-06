package fi.vm.sade.valintatulosservice.json

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.ServletSpecification
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class StreamingJsonArrayRetrieverSpec extends ServletSpecification {
  step(appConfig.start)

  "StreamingJsonArrayRetriever" should {
    "Return contents of JSON array via an iterator" in {
      val retriever = new StreamingJsonArrayRetriever(appConfig)

      useFixture("hyvaksytty-kesken-julkaistavissa.json")
      val hakuOid = "1.2.246.562.5.2013080813081926341928"

      val hakijaDtos = new mutable.MutableList[HakijaDTO]
      hakijaDtos.size must_== 0

      val localUrl = s"$baseUrl/haku/$hakuOid/sijoitteluajo/latest/hakemukset"
      retriever.processStreaming("/any-service", localUrl, classOf[HakijaDTO], hakijaDtos.+=)
      val firstResult = hakijaDtos.head
      firstResult.getHakijaOid must_== "1.2.246.562.24.14229104472"
      firstResult.getHakemusOid must_== "1.2.246.562.11.00000441369"
      firstResult.getHakutoiveet.size must_== 2
      hakijaDtos.size must_== 1
    }
  }
}
