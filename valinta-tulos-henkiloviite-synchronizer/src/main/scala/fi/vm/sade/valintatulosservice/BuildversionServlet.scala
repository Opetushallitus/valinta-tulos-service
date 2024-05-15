package fi.vm.sade.valintatulosservice

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

class BuildversionServlet() extends HttpServlet {
  override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
      writeResponse(200, "https://dashboard.ops.opintopolku.fi", response)
  }

  private def writeResponse(status:Int, message:String, response: HttpServletResponse ) = {
    response.setStatus(status)
    response.setCharacterEncoding("UTF-8")
    response.setContentType("text/plain")
    response.getOutputStream.println(message)
    response.getOutputStream.flush()
    response.getOutputStream.close()
  }
}
