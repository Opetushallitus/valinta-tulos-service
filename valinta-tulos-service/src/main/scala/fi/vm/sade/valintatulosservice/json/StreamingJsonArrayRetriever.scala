package fi.vm.sade.valintatulosservice.json

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.HttpHeaders
import fi.vm.sade.utils.cas.CasClient.JSessionId
import fi.vm.sade.utils.cas.CasParams
import fi.vm.sade.utils.slf4j.Logging
import fi.vm.sade.valintatulosservice.config.VtsAppConfig.VtsAppConfig
import org.http4s.Status

import scala.concurrent.duration.Duration
import scalaj.http.{Http, HttpRequest, HttpResponse}
import scalaz.concurrent.Task
import scalaz.stream._


class StreamingJsonArrayRetriever(appConfig: VtsAppConfig) extends Logging {
  private val jsonFactory = new JsonFactory()
  private val mapper = new ObjectMapper()

  def processStreaming[T,R](targetService: String, url: String, targetClass: Class[T], processSingleItem: T => R, responseIsArray: Boolean = true): Unit = {
    logger.debug(s"Making a request to $url")
    val casParams = createCasParams(appConfig, targetService)
    val jsessionId = authenticate(casParams)

    val request: HttpRequest = Http(url)
      .header(HttpHeaders.COOKIE, s"JSESSIONID=$jsessionId")
      .header("clientSubSystemCode", "valinta-tulos-service")
      .header("Caller-id", "valinta-tulos-service")
      .compress(false)
      .timeout(Duration(1, TimeUnit.MINUTES).toMillis.toInt, Duration(60, TimeUnit.MINUTES).toMillis.toInt)

    var count = 0
    val response: HttpResponse[Unit] = request.execute[Unit](inputStream => {
      logger.debug(s"Starting to process inputstream of response from $url")
      val jsonParser = jsonFactory.createParser(inputStream)
      var currentToken: JsonToken = null

      var arrayStartedOrDocumentFinished = false
      while (responseIsArray && !arrayStartedOrDocumentFinished) {
        currentToken = jsonParser.nextToken()
        arrayStartedOrDocumentFinished = JsonToken.START_ARRAY == currentToken || currentToken == null
      }

      currentToken = jsonParser.nextToken() // advance to beginning of first object
      if (currentToken != JsonToken.START_OBJECT) {
        logger.info(s"No objects found in response")
      } else {
        while (currentToken == JsonToken.START_OBJECT) {
          val parsed = parseObject(mapper, jsonParser, targetClass).getOrElse(throw new RuntimeException(s"Could not parse $targetClass object"))
          currentToken = jsonParser.nextToken()
          processSingleItem(parsed)
          count = count + 1
          if (count % 1000 == 0) {
            logger.info(s"...processed $count items so far...")
          }
        }
      }
    })
    if (response.code == Status.Ok.code) {
      logger.debug(s"Processed $count items of response with status ${response.code} from $url")
    } else if (looksLikeCasRedirect(response)) {
      logger.info("Looks like server JSessionId got old, let's fetch new JSessionId")
      JSessionIdHolder.clear()
      processStreaming(targetService, url, targetClass, processSingleItem, responseIsArray)
    } else {
      logger.warn(s"Got non-OK response code ${response.code} from $url")
      response.headers.foreach { case (header: String, value: String) =>
        logger.debug(s"$header: $value")
      }
    }
  }

  private def looksLikeCasRedirect[R, T](response: HttpResponse[Unit]): Boolean = {
    response.code == Status.Found.code
  }

  private def parseObject[T](mapper: ObjectMapper, jsonParser: JsonParser, targetClass: Class[T]): Option[T] = try {
    Some(mapper.readValue(jsonParser, targetClass))
  } catch {
    case e: Exception =>
      logger.error(s"Could not parse $targetClass", e)
      None
  }

  private def authenticate(casParams: CasParams): String = {
    val sessions: Process[Task, JSessionId] = Process(casParams).toSource through appConfig.securityContext.casClient.sessionRefreshChannel

    JSessionIdHolder.synchronized {
      if (JSessionIdHolder.hasSessionId) {
        JSessionIdHolder.jSessionId.get()
      } else {
        var jSessionId = JSessionIdHolder.NOT_FETCHED
        sessions.map(sessionIdFromCas => jSessionId = sessionIdFromCas).run.run
        JSessionIdHolder.jSessionId.set(jSessionId)
        jSessionId
      }
    }
  }

  private def createCasParams(appConfig: VtsAppConfig, targetService: String): CasParams = {
    CasParams(targetService, appConfig.settings.securitySettings.casUsername, appConfig.settings.securitySettings.casPassword)
  }
}

object JSessionIdHolder {
  val NOT_FETCHED = "<not fetched>"
  val jSessionId = new AtomicReference[String](NOT_FETCHED)

  def hasSessionId: Boolean = jSessionId.get() != NOT_FETCHED

  def clear(): Unit = JSessionIdHolder.synchronized {
    jSessionId.set(NOT_FETCHED)
  }
}
