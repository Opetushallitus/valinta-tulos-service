package fi.vm.sade.valintatulosservice.valintarekisteri.domain

/**
  * You can throw this exception to signal that HTTP 404 should be returned,
  * but you need to add handling it to each endpoint you want to return 404 from.
  * The general error handling in <code>VtsServletBase</code> treats all 404
  * situations as bad paths.
  *
  * @see fi.vm.sade.valintatulosservice.VtsServletBase#notFound
  */
class NotFoundException(message: String) extends RuntimeException(message)
