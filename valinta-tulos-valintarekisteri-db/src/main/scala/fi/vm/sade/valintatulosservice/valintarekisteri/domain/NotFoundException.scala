package fi.vm.sade.valintatulosservice.valintarekisteri.domain

/**
  * Throw this exception to return HTTP 404.
  *
  * @see fi.vm.sade.valintatulosservice.VtsServletBase#error
  */
class NotFoundException(message: String) extends RuntimeException(message)
