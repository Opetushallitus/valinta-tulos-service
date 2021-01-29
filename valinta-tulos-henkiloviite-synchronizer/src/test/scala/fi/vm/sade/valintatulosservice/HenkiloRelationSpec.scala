package fi.vm.sade.valintatulosservice

import org.json4s.DefaultReaders.arrayReader
import org.json4s.native.JsonMethods.parse
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

import scala.io.Source.fromInputStream

@RunWith(classOf[JUnitRunner])
class HenkiloRelationSpec extends Specification {
  "relatedHenkilot" >> {
    "returns a seq per master oid" >> {
      val related = HenkiloviiteSynchronizer.relatedHenkilot(
        List(
          Henkiloviite("master1", "slave1"),
          Henkiloviite("master2", "slave2")
        )
      )
      related.find(_.contains("master1")) must beSome((_: Seq[String]) must contain("slave1"))
      related.find(_.contains("master2")) must beSome((_: Seq[String]) must contain("slave2"))
    }
    "returns a seqs that contain master with all slaves" >> {
      val related = HenkiloviiteSynchronizer.relatedHenkilot(
        List(
          Henkiloviite("master1", "slave1"),
          Henkiloviite("master1", "slave2"),
          Henkiloviite("master2", "slave3")
        )
      )
      related.find(_.contains("master1")) must beSome((_: Seq[String]) must (contain("slave1", "slave2") and not(contain("slave3"))))
    }
  }
  "allPairs" >> {
    val pairs = HenkiloviiteSynchronizer.allPairs(List("A", "B", "C", "D"))
    pairs must contain(
      ("A", "B"), ("B", "A"),
      ("A", "C"), ("C", "A"),
      ("A", "D"), ("D", "A"),
      ("B", "C"), ("C", "B"),
      ("B", "D"), ("D", "B"),
      ("C", "D"), ("D", "C")
    )
  }
  "henkiloRelations" >> {
    val relations = HenkiloviiteSynchronizer.henkiloRelations(List(
      Henkiloviite("master1", "slave1"),
      Henkiloviite("master1", "slave2"),
      Henkiloviite("master1", "slave3"),
      Henkiloviite("master2", "slave4"),
      Henkiloviite("master2", "slave5"),
      Henkiloviite("master3", "slave6")
    ))
    relations must contain(HenkiloRelation("master1", "slave1"), HenkiloRelation("slave1", "master1"))
    relations must contain(HenkiloRelation("master1", "slave2"), HenkiloRelation("slave2", "master1"))
    relations must contain(HenkiloRelation("master1", "slave3"), HenkiloRelation("slave3", "master1"))

    relations must contain(HenkiloRelation("master2", "slave4"), HenkiloRelation("slave4", "master2"))
    relations must contain(HenkiloRelation("master2", "slave5"), HenkiloRelation("slave5", "master2"))

    relations must contain(HenkiloRelation("master3", "slave6"), HenkiloRelation("slave6", "master3"))
  }
  "henkiloRelations with big data" >> {
    implicit val henkiloviiteReader = HenkiloviiteClient.henkiloviiteReader
    val data = fromInputStream(getClass.getResourceAsStream("/viitteet.json")).mkString
    val viitteet = arrayReader[Henkiloviite].read(parse(data))
    val start = System.currentTimeMillis()
    val relations = HenkiloviiteSynchronizer.henkiloRelations(viitteet)
    (System.currentTimeMillis() - start) must beLessThan[Long](500)
    relations.size must_== 27018
  }
}
