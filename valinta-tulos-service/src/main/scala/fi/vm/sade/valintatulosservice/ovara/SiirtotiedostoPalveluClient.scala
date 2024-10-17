package fi.vm.sade.valintatulosservice.ovara


import fi.vm.sade.valinta.dokumenttipalvelu.SiirtotiedostoPalvelu
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.Serialization.{write, writePretty}
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.{IlmoittautumistilaSerializer, ValinnantilaSerializer, VastaanottoActionSerializer}
import fi.vm.sade.valintatulosservice.ovara.config.SiirtotiedostoConfig
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.Oids

import java.io.ByteArrayInputStream

class SiirtotiedostoPalveluClient(config: SiirtotiedostoConfig) extends Logging {
  val siirtotiedostoPalvelu = new SiirtotiedostoPalvelu(config.aws_region, config.s3_bucket, config.role_arn)
  logger.info(s"Created siirtotiedostoclient with config $config")
  val saveRetryCount = 2

  implicit val formats: Formats = DefaultFormats ++ Oids.getSerializers() ++
    List(new ValinnantilaSerializer, new VastaanottoActionSerializer, new IlmoittautumistilaSerializer)

  def saveSiirtotiedosto[T](contentType: String,
                            content: Seq[T],
                            executionId: String,
                            fileNumber: Int): String = {
    try {
      if (content.nonEmpty) {
        val output = writePretty(Seq(content.head))
        logger.info(s"($executionId) Saving siirtotiedosto... total ${content.length}, first: ${content.head}")
        logger.info(s"($executionId) Saving siirtotiedosto... output: $output")
        siirtotiedostoPalvelu
          .saveSiirtotiedosto(
            "valintarekisteri",
            contentType,
            "",
            executionId,
            fileNumber,
            new ByteArrayInputStream(write(content).getBytes()),
            saveRetryCount).key
      } else {
        logger.info("($executionId) Ei tallennettavaa!")
        ""
      }
    } catch {
      case e: Exception =>
        logger.error(s"($executionId) Siirtotiedoston tallennus s3-ämpäriin epäonnistui...")
        throw e
    }
  }
}