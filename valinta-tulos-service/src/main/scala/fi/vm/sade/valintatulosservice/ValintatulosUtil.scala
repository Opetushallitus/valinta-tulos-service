package fi.vm.sade.valintatulosservice

import java.util
import scala.collection.JavaConverters._

object ValintatulosUtil {
  def toSortedSet[T](source: List[T]): util.TreeSet[T] = {
    val s = new util.TreeSet[T]
    s.addAll(source.asJava)
    s
  }
}
