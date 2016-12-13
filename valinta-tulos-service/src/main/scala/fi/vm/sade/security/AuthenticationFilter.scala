package fi.vm.sade.security

import org.scalatra.ScalatraFilter

class AuthenticationFilter(loginUrl: String) extends ScalatraFilter {
  before() {
    if (!session.contains("authenticated")) {
      session += ("authenticated" -> false)
      if (request.requestMethod == org.scalatra.Get) {
        session += ("redirect_to" -> requestPath)
      }
      redirect(loginUrl)
    }
  }
}
