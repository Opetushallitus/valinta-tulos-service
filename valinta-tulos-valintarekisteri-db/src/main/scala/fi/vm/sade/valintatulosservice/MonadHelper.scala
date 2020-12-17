package fi.vm.sade.valintatulosservice

import scala.annotation.tailrec

object MonadHelper {
  def sequence[L, R](xs: Traversable[Either[L, R]]): Either[L, List[R]] = {
    @tailrec def go(xs: Traversable[Either[L, R]], acc: Vector[R]): Either[L, List[R]] =
      xs.headOption match {
        case Some(Right(x)) => go(xs.tail, acc :+ x)
        case Some(Left(t))  => Left(t)
        case None           => Right(acc.toList)
      }
    go(xs, Vector.empty)
  }
}
