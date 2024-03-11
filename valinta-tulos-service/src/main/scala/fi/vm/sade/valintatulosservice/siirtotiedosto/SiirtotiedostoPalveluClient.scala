package fi.vm.sade.valintatulosservice.siirtotiedosto


import fi.vm.sade.valinta.dokumenttipalvelu.SiirtotiedostoPalvelu
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization.{write, writePretty}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Oids

import java.io.ByteArrayInputStream

class SiirtotiedostoPalveluClient(region: String, bucket: String) extends Logging {
  lazy val siirtotiedostoPalvelu = new SiirtotiedostoPalvelu(region, bucket)
  val saveRetryCount = 2

  implicit val formats: Formats = DefaultFormats ++ Oids.getSerializers()

  def saveSiirtotiedosto[T](contentType: String,
                            content: Seq[T]): String = {
    try {
      if (content.nonEmpty) {
        val output = writePretty(Seq(content.head))
        logger.info(s"Saving siirtotiedosto... total ${content.length}, first: ${content.head}")
        logger.info(s"Saving siirtotiedosto... output: $output")
        siirtotiedostoPalvelu
          .saveSiirtotiedosto(
            "todo_add_formatted_time",
            "",
            "valintarekisteri",
            contentType,
            new ByteArrayInputStream(write(content).getBytes()),
            saveRetryCount).key
      } else {
        logger.info("Ei tallennettavaa!")
        ""
      }
    } catch {
        case e: Exception =>
          logger.error(s"Siirtotiedoston tallennus s3-ämpäriin epäonnistui...")
          throw e
    }
  }
}