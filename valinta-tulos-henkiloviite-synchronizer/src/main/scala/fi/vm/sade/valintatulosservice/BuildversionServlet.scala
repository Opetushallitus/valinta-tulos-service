package fi.vm.sade.valintatulosservice

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class BuildversionServlet(buildversion: Buildversion) extends HttpServlet {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
    writeResponse(200, buildversion.toString, response)
  }

  private def writeResponse(status: Int, message: String, response: HttpServletResponse) = {
    response.setStatus(status)
    response.setCharacterEncoding("UTF-8")
    response.setContentType("text/plain")
    response.getOutputStream.println(message)
    response.getOutputStream.flush()
    response.getOutputStream.close()
  }
}
