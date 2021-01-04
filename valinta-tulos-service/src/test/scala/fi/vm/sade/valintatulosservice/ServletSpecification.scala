package fi.vm.sade.valintatulosservice

import fi.vm.sade.valintatulosservice.hakemus.HakemusFixtures
import fi.vm.sade.valintatulosservice.json.JsonFormats
import org.scalatra.test.HttpComponentsClient
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

trait ServletSpecification extends Specification with ITSetup with TimeWarp with BeforeAll {
  sequential

  def baseUrl = "http://localhost:" + SharedJetty.port + "/valinta-tulos-service"
  implicit def formats = JsonFormats.jsonFormats
  override lazy val hakemusFixtureImporter = HakemusFixtures()

  protected val httpComponentsClient = new HttpComponentsClient {
    override def baseUrl: String = ServletSpecification.this.baseUrl
  }

  override def beforeAll(): Unit = {
    SharedJetty.start
  }

  def postJSON[T](path: String, body: String, headers: Map[String, String] = Map.empty)(
    block: => T
  ): T = {
    httpComponentsClient.post(
      path,
      body.getBytes("UTF-8"),
      Map("Content-type" -> "application/json") ++ headers
    )(block)
  }

  def patchJSON[T](path: String, body: String, headers: Map[String, String] = Map.empty)(
    block: => T
  ): T = {
    httpComponentsClient.patch(
      path,
      body.getBytes("UTF-8"),
      Map("Content-type" -> "application/json") ++ headers
    )(block)
  }

  def get[A](uri: String)(f: => A): A = httpComponentsClient.get(uri)(f)
  def get[A](uri: String, params: Tuple2[String, String]*)(f: => A): A =
    httpComponentsClient.get(uri, params)(f)
  def get[A](
    uri: String,
    params: Iterable[(String, String)] = Seq.empty,
    headers: Map[String, String] = Map.empty
  )(f: => A): A =
    httpComponentsClient.get(uri, params, headers)(f)
  def post[A](uri: String, params: Tuple2[String, String]*)(f: => A): A =
    httpComponentsClient.post(uri, params)(f)
  def post[A](uri: String, params: Iterable[(String, String)])(f: => A): A =
    httpComponentsClient.post(uri, params)(f)
  def post[A](uri: String, params: Iterable[(String, String)], headers: Map[String, String])(
    f: => A
  ): A = httpComponentsClient.post(uri, params, headers)(f)
  def post[A](uri: String, body: Array[Byte] = Array(), headers: Map[String, String] = Map.empty)(
    f: => A
  ): A = httpComponentsClient.post(uri, body, headers)(f)
  def post[A](uri: String, params: Iterable[(String, String)], files: Iterable[(String, Any)])(
    f: => A
  ): A = httpComponentsClient.post(uri, params, files)(f)
  def post[A](
    uri: String,
    params: Iterable[(String, String)],
    files: Iterable[(String, Any)],
    headers: Map[String, String]
  )(f: => A): A =
    httpComponentsClient.post(uri, params, files, headers)(f)
  def put[A](uri: String, params: Tuple2[String, String]*)(f: => A): A =
    httpComponentsClient.put(uri, params)(f)
  def put[A](uri: String, params: Iterable[(String, String)])(f: => A): A =
    httpComponentsClient.put(uri, params)(f)
  def put[A](uri: String, params: Iterable[(String, String)], headers: Map[String, String])(
    f: => A
  ): A =
    httpComponentsClient.put(uri, params, headers)(f)
  def put[A](uri: String, body: Array[Byte] = Array(), headers: Map[String, String] = Map.empty)(
    f: => A
  ): A =
    httpComponentsClient.put(uri, body, headers)(f)
  def put[A](uri: String, params: Iterable[(String, String)], files: Iterable[(String, Any)])(
    f: => A
  ): A =
    httpComponentsClient.put(uri, params, files)(f)
  def put[A](
    uri: String,
    params: Iterable[(String, String)],
    files: Iterable[(String, Any)],
    headers: Map[String, String]
  )(f: => A): A =
    httpComponentsClient.put(uri, params, files, headers)(f)

  def delete[A](
    uri: String,
    params: Iterable[(String, String)] = Seq.empty,
    headers: Map[String, String] = Map.empty
  )(f: => A): A =
    httpComponentsClient.delete(uri, params, headers)(f)

  def patch[A](uri: String, params: Tuple2[String, String]*)(f: => A): A =
    httpComponentsClient.patch(uri, params)(f)
  def patch[A](uri: String, params: Iterable[(String, String)])(f: => A): A =
    httpComponentsClient.patch(uri, params)(f)
  def patch[A](uri: String, params: Iterable[(String, String)], headers: Map[String, String])(
    f: => A
  ): A =
    httpComponentsClient.patch(uri, params, headers)(f)
  def patch[A](uri: String, body: Array[Byte] = Array(), headers: Map[String, String] = Map.empty)(
    f: => A
  ): A =
    httpComponentsClient.patch(uri, body, headers)(f)

  def body: String = httpComponentsClient.body
  def status: Int = httpComponentsClient.status
}
