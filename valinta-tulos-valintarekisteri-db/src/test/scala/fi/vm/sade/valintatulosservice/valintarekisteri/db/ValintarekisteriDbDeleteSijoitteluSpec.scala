package fi.vm.sade.valintatulosservice.valintarekisteri.db

import fi.vm.sade.valintatulosservice.logging.Logging
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.HakuOid
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeAfterEach
import slick.jdbc.PostgresProfile.api._

import scala.util.Try

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbDeleteSijoitteluSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAfterEach with Logging {
  sequential
  step(appConfig.start)
  step(deleteAll())

  private val hakuOid1 = HakuOid("1.2.246.561.76.00000000051")
  private val hakuOid2 = HakuOid("1.2.246.561.76.00000000052")

  "ValintarekisteriDb" should {
    "list sijoitteluajo counts correctly" in {
      checkCounts(Seq((hakuOid1, 4), (hakuOid2, 3)))
      setPoistonesto()
      checkCounts(Seq((hakuOid1, 3), (hakuOid2, 3)))
    }
    "find correct sijoitteluajot for delete" in {
      checkDeleted(hakuOid1, List(11l, 12l), 2)
      setPoistonesto()
      checkDeleted(hakuOid1, List(12l), 2)
    }
    "delete sijoitteluajo" in {
      sijoitteluajoExists(12l) must_== true
      checkCounts(Seq((hakuOid1, 4), (hakuOid2, 3)))
      singleConnectionValintarekisteriDb.deleteSijoitteluajo(hakuOid1, 12l)
      sijoitteluajoExists(12l) must_== false
      checkCounts(Seq((hakuOid1, 3), (hakuOid2, 3)))
    }
    "don't delete sijoitteluajo with poistonesto" in {
      setPoistonesto()
      sijoitteluajoExists(11l) must_== true
      Try(singleConnectionValintarekisteriDb.deleteSijoitteluajo(hakuOid1, 11l)).isFailure must_== true
      sijoitteluajoExists(11l) must_== true
    }
    "don't delete latest sijoitteluajo" in {
      sijoitteluajoExists(14l) must_== true
      checkCounts(Seq((hakuOid1, 4), (hakuOid2, 3)))
      Try(singleConnectionValintarekisteriDb.deleteSijoitteluajo(hakuOid1, 14l)).isFailure must_== true
      sijoitteluajoExists(14l) must_== true
      checkCounts(Seq((hakuOid1, 4), (hakuOid2, 3)))
    }
  }

  def sijoitteluajoExists(id:Long) = singleConnectionValintarekisteriDb.getSijoitteluajo(id).isDefined

  def checkCounts(expected:Seq[(HakuOid,Long)]) = {
    val counts = singleConnectionValintarekisteriDb.listHakuAndSijoitteluAjoCount()
    counts.size must_== expected.size
    counts.diff(expected) must_== Seq()
  }

  def checkDeleted(hakuOid: HakuOid, expected:List[Long], limit:Int = 2) = {
    val toBeDeleted = singleConnectionValintarekisteriDb.findSijoitteluAjotSkippingFirst(hakuOid, limit)
    toBeDeleted.size must_== expected.size
    toBeDeleted.diff(expected) must_== List()
  }

  def setPoistonesto() = singleConnectionValintarekisteriDb.runBlocking(sqlu"""update sijoitteluajot set poistonesto = true where id = ${11l}""")

  def storeSijoittelut() = {
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(11l, hakuOid1, 2))
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(12l, hakuOid1, 2, false))
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(13l, hakuOid1, 2, false))
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(14l, hakuOid1, 2, false))

    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(21l, hakuOid2, 2))
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(22l, hakuOid2, 2, false))
    singleConnectionValintarekisteriDb.storeSijoittelu(createHugeSijoittelu(23l, hakuOid2, 2, false))
  }

  override protected def before: Unit = {
    storeSijoittelut()
  }
  override protected def after: Unit = {
    deleteAll()
  }

  step(deleteAll())
}
