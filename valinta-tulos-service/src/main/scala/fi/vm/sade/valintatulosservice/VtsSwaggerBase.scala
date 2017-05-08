package fi.vm.sade.valintatulosservice

import org.scalatra.swagger.{ResponseMessage, SwaggerSupport}

trait VtsSwaggerBase { this: SwaggerSupport =>
  case class ErrorResponse(error: String)

  registerModel[ErrorResponse]()

  class ModelResponseMessage(override val code: Int, override val message: String)
    extends ResponseMessage(code, message, responseModel = Some("ErrorResponse"))

  object ModelResponseMessage {
    def apply(code: Int, message: String): ModelResponseMessage = new ModelResponseMessage(code, message)
  }
}
