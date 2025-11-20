package fi.vm.sade.valintatulosservice.kayttooikeus

import fi.vm.sade.security.AuthenticationFailedException
import fi.vm.sade.valintatulosservice.config.AppConfig
import fi.vm.sade.valintatulosservice.http.DefaultHttpClient
import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.security.Role
import org.json4s.jackson.JsonMethods.parse
import scalaj.http.HttpOptions

import scala.util.Try
import scala.util.control.NonFatal

class KayttooikeusUserDetailsService(appConfig:AppConfig) extends Logging {
  import org.json4s._
  implicit val formats = DefaultFormats

  def getUserByUsername(username: String): Either[Throwable, KayttooikeusUserDetails] = {
    val url = appConfig.ophUrlProperties.url("kayttooikeus-service.userDetails.byUsername", username)

    fetch(url) { response =>
      // response username field contains actually oid because of historical ldap reasons
      val koDto = parse(response).extract[KayttooikeusUserResp]
      KayttooikeusUserDetails(koDto.authorities.map(x => Role(x.authority.replace("ROLE_",""))).toSet, koDto.username)
    }.left.map {
      case e: IllegalArgumentException => new AuthenticationFailedException(s"User not found with username: $username", e)
      case e: Exception => new RuntimeException(s"Failed to get username $username details", e)
    }
  }

  private def fetch[T](url: String)(parse: (String => T)): Either[Throwable, T] = {
    Try(DefaultHttpClient.httpGet(
      url,
      HttpOptions.connTimeout(5000),
      HttpOptions.readTimeout(10000)
    )("valinta-tulos-service")
      .responseWithHeaders match {
      case (200, _, resultString) =>
        Try(Right(parse(resultString))).recover {
          case NonFatal(e) => Left(new IllegalStateException(s"Parsing result $resultString of GET $url failed", e))
        }.get
      case (404, _, resultString) =>
        Left(new IllegalArgumentException(s"User not found"))
      case (responseCode, _, resultString) =>
        Left(new RuntimeException(s"GET $url failed with status $responseCode: $resultString"))
    }).recover {
      case NonFatal(e) => Left(new RuntimeException(s"GET $url failed", e))
    }.get
  }

}

