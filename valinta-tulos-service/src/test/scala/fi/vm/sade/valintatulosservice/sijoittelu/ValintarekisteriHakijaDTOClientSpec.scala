package fi.vm.sade.valintatulosservice.sijoittelu

import fi.vm.sade.sijoittelu.tulos.dto.raportointi.HakijaDTO
import fi.vm.sade.valintatulosservice.ITSpecification
import fi.vm.sade.valintatulosservice.valintarekisteri.domain.{HakemusOid, HakuOid}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.collection.mutable.ListBuffer

@RunWith(classOf[JUnitRunner])
class ValintarekisteriHakijaDTOClientSpec extends ITSpecification with ValintarekisteriTestData {
  step(deleteAll())

  lazy val client = new ValintarekisteriHakijaDTOClientImpl(
    new ValintarekisteriRaportointiServiceImpl(
      singleConnectionValintarekisteriDb,
      new ValintarekisteriValintatulosDaoImpl(singleConnectionValintarekisteriDb)
    ),
    new ValintarekisteriSijoittelunTulosClientImpl(singleConnectionValintarekisteriDb),
    singleConnectionValintarekisteriDb
  )

  step(createSijoitteluajoHaulle2)
  step(createHakujen1Ja2ValinnantuloksetIlmanSijoittelua)
  step(
    insertValinnantulos(
      hakuOid2,
      valinnantulos(oidHaku2hakukohde1, oidHaku2hakukohde1jono1, sijoittelunHakemusOid2)
    )
  )
  step(
    insertValinnantulos(
      hakuOid2,
      valinnantulosHylatty(oidHaku2hakukohde1, oidHaku2hakukohde1jono2, sijoittelunHakemusOid2)
    )
  )

  "processSijoittelunTulokset" should {
    def test(sijoitteluajoId: String, hakuOid: HakuOid, expectedSize: Int) = {
      val list = new ListBuffer[HakemusOid]

      client.processSijoittelunTulokset(
        HakijaDTOSearchCriteria(hakuOid, sijoitteluajoId),
        (hakija: HakijaDTO) => list += HakemusOid(hakija.getHakemusOid)
      )

      list.distinct.size must_== expectedSize
    }

    "process 'latest' sijoitteluajo" in {
      test("latest", hakuOid2, 20)
    }
    "process sijoitteluajo with id" in {
      test("" + sijoitteluajoId, hakuOid2, 20)
    }
    "process 'latest' erillishaku" in {
      test("latest", hakuOid1, 4)
    }
    "process erillishaku with negative id" in {
      test("-1", hakuOid1, 4)
    }
    "process nonsense" in {
      test("foo", hakuOid1, 0)
    }
  }
}
