package fi.vm.sade.valintatulosservice.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.`type`.MapType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.hubspot.jinjava.Jinjava

import java.net.URL
import java.util
import scala.collection.JavaConverters._

object JinjaTemplateProcessor {
  private val jinjava = new Jinjava()

  def processJinjaWithYamlAttributes(templateUrl: URL, vars: URL): String = {
    val mapper: ObjectMapper = new ObjectMapper(new YAMLFactory())
    val mapType: MapType = mapper.getTypeFactory.constructMapType(classOf[util.HashMap[String, String]], classOf[String], classOf[String])
    val rawValue = mapper.readValue(vars, mapType).asInstanceOf[util.HashMap[String, String]]
    val attributes: Map[String, Any] = rawValue.asScala.toMap.asInstanceOf[Map[String, Any]]
    val template = Resources.toString(templateUrl, Charsets.UTF_8)
    jinjava.render(template, attributes.asJava)
  }
}
