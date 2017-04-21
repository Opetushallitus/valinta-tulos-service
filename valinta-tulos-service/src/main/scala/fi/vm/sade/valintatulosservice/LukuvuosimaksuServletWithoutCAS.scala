package fi.vm.sade.valintatulosservice

import java.util.Date

import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import fi.vm.sade.valintatulosservice.lukuvuosimaksut.LukuvuosimaksuMuutos
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{Lukuvuosimaksu, Maksuntila}
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer
import org.scalatra.swagger.Swagger
import org.scalatra.{InternalServerError, NoContent, Ok}

import scala.util.Try


class LukuvuosimaksuServletWithoutCAS(lukuvuosimaksuService: LukuvuosimaksuService)
                                  (implicit val swagger: Swagger, appConfig: VtsAppConfig)
  extends VtsServletBase {

  implicit val defaultFormats = DefaultFormats + new EnumNameSerializer(Maksuntila)

  override val applicationName = Some("lukuvuosimaksut")

  override protected def applicationDescription: String = "Lukuvuosimaksut unauthenticated REST API"

  def authenticatedPersonOid: String = {
    Option(params("kutsuja")).getOrElse(throw new RuntimeException("Query parameter kutsuja is missing!"))
  }

  get("/:hakukohdeOid") {
    val hakukohdeOid = hakukohdeOidParam
    val lukuvuosimaksus = lukuvuosimaksuService.getLukuvuosimaksut(hakukohdeOid, null)
    val result = lukuvuosimaksus.groupBy(l => l.personOid).values.map(l => l.sortBy(a => a.luotu).reverse)
      .map(l => l.head).toList

    Ok(result)
  }


  post("/:hakukohdeOid") {
    val muokkaaja = authenticatedPersonOid

    val hakukohdeOid = hakukohdeOidParam

    Try(parsedBody.extract[List[LukuvuosimaksuMuutos]]).getOrElse(Nil) match {
      case eimaksuja if eimaksuja.isEmpty =>
        InternalServerError("No 'lukuvuosimaksuja' in request body!")
      case lukuvuosimaksuMuutokset =>
        val lukuvuosimaksut = lukuvuosimaksuMuutokset.map(m => {
          Lukuvuosimaksu(m.personOid, hakukohdeOid, m.maksuntila, muokkaaja, new Date)
        })
        lukuvuosimaksuService.updateLukuvuosimaksut(lukuvuosimaksut, null)

        NoContent()
    }
  }

  private def hakukohdeOidParam: String = {
    Try(params("hakukohdeOid")).toOption.filter(!_.isEmpty)
      .getOrElse(throw new RuntimeException("HakukohdeOid is mandatory!"))
  }
}
