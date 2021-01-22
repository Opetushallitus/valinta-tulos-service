package fi.vm.sade.valintatulosservice.valintarekisteri.db

import java.time.Instant
import java.util.ConcurrentModificationException

import fi.vm.sade.valintatulosservice.valintarekisteri.db.ehdollisestihyvaksyttavissa.{GoneException, HyvaksynnanEhto}
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{EiKktutkintoonJohtavaHakukohde, HakemusOid, HakuOid, HakukohdeOid, HakukohdeRecord, Hyvaksytty, Kausi, ValinnantilanTallennus, ValintatapajonoOid}
import fi.vm.sade.valintatulosservice.valintarekisteri.{ITSetup, ValintarekisteriDbTools}
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{AfterAll, BeforeAll, BeforeEach}
import slick.jdbc.PostgresProfile.api._

@RunWith(classOf[JUnitRunner])
class ValintarekisteriDbHyvaksynnanEhtoSpec extends Specification with ITSetup with ValintarekisteriDbTools with BeforeAll with BeforeEach with AfterAll {
  sequential

  def beforeAll: Unit = {
    appConfig.start
  }

  def before: Any = {
    deleteAll()
  }

  def afterAll: Unit = {
    deleteAll()
  }

  val hakemusOid = HakemusOid("1.2.246.562.11.00000000000000000001")
  val hakukohdeOid = HakukohdeOid("1.2.246.562.20.00000000001")
  val ehto = HyvaksynnanEhto("muu", "muu", "andra", "other")
  val ilmoittaja = "1.2.246.562.24.00000000002"
  val hakukohdeRecord = EiKktutkintoonJohtavaHakukohde(hakukohdeOid, HakuOid(""), Some(Kausi("2000S")))
  val valinnantilanTallennus = ValinnantilanTallennus(
    hakemusOid,
    ValintatapajonoOid("valintatapajonoOid"),
    hakukohdeOid,
    "1.2.246.562.24.00000000001",
    Hyvaksytty,
    "muokkaaja")

  "ValintareksiteriDb" should {
    "store ehdollisesti hyväksyttävissä hakukohteessa" in {
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      result._1 should_== ehto
      getRows should_== Vector(("muu", "muu", "andra", "other"))
    }

    "not store ehdollisesti hyväksyttävissä hakukohteessa if one exists" in {
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto.copy(fi = "toinen muu"),
          ilmoittaja)) must throwAn[ConcurrentModificationException]
      getRows should_== Vector(("muu", "muu", "andra", "other"))
    }

    "not store ehdollisesti hyväksyttävissä hakukohteessa if valinnantulos exists" in {
      singleConnectionValintarekisteriDb.storeHakukohde(hakukohdeRecord)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus, None))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja)) must throwAn[GoneException]
      getRows should_== Vector.empty
    }

    "update ehdollisesti hyväksyttävissä hakukohteessa" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      val updatedEhto = ehto.copy(fi = "toinen muu")
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          updatedEhto,
          ilmoittaja,
          lastModified))
      result._1 should_== updatedEhto
      getRows should_== Vector(("muu", "toinen muu", "andra", "other"))
    }

    "not update ehdollisesti hyväksyttävissä hakukohteessa if modified since" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto.copy(fi = "toinen muu"),
          ilmoittaja,
          lastModified))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja,
          lastModified)) must throwAn[ConcurrentModificationException]
      getRows should_== Vector(("muu", "toinen muu", "andra", "other"))
    }

    "not update ehdollisesti hyväksyttävissä hakukohteessa if valinnantulos exists" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.storeHakukohde(hakukohdeRecord)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus, None))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto.copy(fi = "toinen muu"),
          ilmoittaja,
          lastModified)) must throwAn[GoneException]
      getRows should_== Vector(("muu", "muu", "andra", "other"))
    }

    "not update ehdollisesti hyväksyttävissä hakukohteessa if no changes" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja,
          lastModified)) must throwAn[ConcurrentModificationException]
      getRows should_== Vector(("muu", "muu", "andra", "other"))
    }

    "delete ehdollisesti hyväksyttävissä hakukohteessa" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      val result = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.deleteHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          lastModified))
      result should_== ehto
      getRows should_== Vector.empty
    }

    "not delete ehdollisesti hyväksyttävissä hakukohteessa if modified since" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.updateHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto.copy(fi = "toinen muu"),
          ilmoittaja,
          lastModified))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.deleteHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          lastModified)) must throwAn[ConcurrentModificationException]
      getRows should_== Vector(("muu", "toinen muu", "andra", "other"))
    }

    "not delete ehdollisesti hyväksyttävissä hakukohteessa if valinnantulos exists" in {
      val (_, lastModified) = singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.insertHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          ehto,
          ilmoittaja))
      singleConnectionValintarekisteriDb.storeHakukohde(hakukohdeRecord)
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.storeValinnantila(valinnantilanTallennus, None))
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.deleteHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          lastModified)) must throwAn[GoneException]
      getRows should_== Vector(("muu", "muu", "andra", "other"))
    }

    "not delete ehdollisesti hyväksyttävissä hakukohteessa if none exists" in {
      singleConnectionValintarekisteriDb.runBlocking(
        singleConnectionValintarekisteriDb.deleteHyvaksynnanEhtoHakukohteessa(
          hakemusOid,
          hakukohdeOid,
          Instant.now())) must throwAn[ConcurrentModificationException]
      getRows should_== Vector.empty
    }
  }

  private def getRows: Vector[(String, String, String, String)] = {
    singleConnectionValintarekisteriDb.runBlocking(
      sql"""select koodi, fi, sv, en
              from hyvaksynnan_ehto_hakukohteessa
              where hakemus_oid = $hakemusOid and
                    hakukohde_oid = $hakukohdeOid
          """.as[(String, String, String, String)]
    )
  }
}
