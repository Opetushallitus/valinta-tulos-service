package fi.vm.sade.utils

import java.util.UUID
import javax.servlet.Servlet

import org.eclipse.jetty.servlet.ServletHolder
import org.scalatra.test.EmbeddedJettyContainer

object ServletTest {
  def withServlet[R](jetty: EmbeddedJettyContainer, servlet: Servlet, thunk: (String) => R): R = {
    val uri = UUID.randomUUID().toString
    val servletHolder = new ServletHolder(uri, servlet)
    jetty.servletContextHandler.synchronized {
      jetty.servletContextHandler.addServlet(servletHolder, s"/$uri/*")
    }
    try thunk(uri)
    finally {
      jetty.servletContextHandler.synchronized {
        jetty.servletContextHandler.getServletHandler.setServletMappings(
          jetty.servletContextHandler.getServletHandler.getServletMappings.filter(m => m.getServletName != servletHolder.getName)
        )
        jetty.servletContextHandler.getServletHandler.setServlets(
          jetty.servletContextHandler.getServletHandler.getServlets.filter(s => s != servletHolder)
        )
      }
    }
  }
}
