package fi.vm.sade.valintatulosservice

import scala.collection.mutable

object MonadHelper {
  def sequence[L, R](xs: Traversable[Either[L, R]]): Either[L, List[R]] = {
    def go(xs: Traversable[Either[L, R]], acc: mutable.MutableList[R]): Either[L, List[R]] = xs.headOption match {
      case Some(Right(x)) => go(xs.tail, acc :+ x)
      case Some(Left(t)) => Left(t)
      case None => Right(acc.toList)
    }
    go(xs, mutable.MutableList.empty)
  }
}
