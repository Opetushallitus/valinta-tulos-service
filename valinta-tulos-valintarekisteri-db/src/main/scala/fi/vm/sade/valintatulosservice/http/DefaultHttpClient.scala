package fi.vm.sade.valintatulosservice.http

import org.asynchttpclient.Dsl.asyncHttpClient
import org.asynchttpclient.{AsyncHttpClient, Response}

import java.time.Duration
import java.util.concurrent.TimeUnit

object DefaultHttpClient {
  val client: AsyncHttpClient = asyncHttpClient()
  private val defaultRequestTimeout: Int = 10000
  private val defaultReadTimeout: Int = 60000

  def httpGet(url: String,
              connTimeout: Int = defaultRequestTimeout,
              readTimeout: Int = defaultReadTimeout,
              extraHeaders: Map[String, String] = Map.empty)(clientCallerId: String): Response = {
    val builder = client.prepareGet(url)
      .setHeader("Caller-Id", clientCallerId)
      .setRequestTimeout(Duration.ofMillis(connTimeout))
      .setReadTimeout(Duration.ofMillis(readTimeout))

    extraHeaders.foreach { case (name, value) => builder.addHeader(name, value) }

    builder.execute().get(connTimeout + readTimeout, TimeUnit.MILLISECONDS)
  }

  def getJson(url: String, connTimeout: Int = defaultRequestTimeout, readTimeout: Int = defaultReadTimeout)(clientCallerId: String): Response =
    httpGet(url, connTimeout, readTimeout, extraHeaders = Map("Content-Type" -> "application/json"))(clientCallerId)

  def postJson(url: String,
               body: String,
               connTimeout: Int = defaultRequestTimeout,
               readTimeout: Int = defaultReadTimeout)(clientCallerId: String): Response = client.preparePost(url)
    .setBody(body)
    .setRequestTimeout(Duration.ofMillis(connTimeout))
    .setReadTimeout(Duration.ofMillis(readTimeout))
    .setHeader("Caller-Id", clientCallerId)
    .setHeader("Content-Type", "application/json")
    .execute()
    .get(connTimeout + readTimeout, TimeUnit.MILLISECONDS)
}
