package fi.vm.sade.security.cas

sealed trait CasResponse {
  def success: Boolean
}

case class CasResponseSuccess(username: String) extends CasResponse { def success = true }
case class CasResponseFailure(errorMessage: String) extends CasResponse { def success = false }
