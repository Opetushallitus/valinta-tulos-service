package fi.vm.sade.valintatulosservice.config

import com.typesafe.config.ConfigFactory
import fi.vm.sade.valintatulosservice.template.JinjaTemplateProcessor

import java.io.{File, IOException, StringReader}
import java.net.{URL, URLConnection, URLStreamHandler}
import java.util.Properties

object ConfigTemplateProcessor {
  def createSettings[T <: BaseApplicationSettings](projectName: String, attributesFile: String)(implicit applicationSettingsParser: ApplicationSettingsParser[T]): T = {
    val templateURL: URL = new URL(
      null,
      "classpath:oph-configuration/" + projectName + ".properties.template",
      new ClassPathUrlHandler(getClass.getClassLoader))
    val attributesURL = new File(attributesFile).toURI.toURL

    val templatedData = JinjaTemplateProcessor.processJinjaWithYamlAttributes(templateURL, attributesURL) + "\nmongodb.ensureIndex=false" // <- to make work with embedded mongo
    parseTemplatedData(templatedData)
  }

  def createSettings[T <: BaseApplicationSettings](template: URL, attributes: URL)(implicit applicationSettingsParser: ApplicationSettingsParser[T]): T = {
    val templatedData: String = JinjaTemplateProcessor.processJinjaWithYamlAttributes(template, attributes) + "\nmongodb.ensureIndex=false" // <- to make work with embedded mongo
    parseTemplatedData(templatedData)
  }

  private def parseTemplatedData[T <: BaseApplicationSettings](templatedData: String)(implicit applicationSettingsParser: ApplicationSettingsParser[T]): T = {
    val properties = new Properties()
    properties.load(new StringReader(templatedData))
    applicationSettingsParser.parse(ConfigFactory.load(ConfigFactory.parseProperties(properties)))
  }
}

class ClassPathUrlHandler(val classLoader: ClassLoader) extends URLStreamHandler {
  @throws[IOException]
  protected def openConnection(u: URL): URLConnection = {
    val resourceUrl = classLoader.getResource(u.getPath)
    resourceUrl.openConnection
  }
}
