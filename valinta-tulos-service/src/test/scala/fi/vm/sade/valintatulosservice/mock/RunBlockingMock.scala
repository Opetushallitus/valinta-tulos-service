package fi.vm.sade.valintatulosservice.mock

import fi.vm.sade.valintatulosservice.valintarekisteri.db.impl.ValintarekisteriRepository
import org.mockito.stubbing.OngoingStubbing
import org.specs2.mock.Mockito
import slick.dbio._

import scala.concurrent.duration.Duration

trait RunBlockingMock { this: Mockito =>

  private def answerRun(params: Any): Either[Throwable, Any] = mockRun(params.asInstanceOf[Array[Any]].head.asInstanceOf[DBIO[Any]])
  private def mockRun(dbio: DBIO[Any]): Either[Throwable, Any] = dbio match {
    case FailureAction(t) => Left(t)
    case SuccessAction(r) => Right(r)
    case FlatMapAction(m, f, _) => mockRun(m).right.flatMap(x => mockRun(f(x)))
    case AndThenAction(actions) =>
      def loop(actions: List[DBIO[Any]]): Either[Throwable, Any] = actions match {
        case a :: Nil => mockRun(a)
        case a :: rest => mockRun(a).right.flatMap(_ => loop(rest))
        case _ => throw new RuntimeException("This should not happen")
      }
      loop(actions.toList)
    case SequenceAction(actions) => {
      val result = actions.toList.map(a => mockRun(a))
      result.find(_.isLeft) match {
        case Some(left) => left
        case None => Right(result.map {
          case Right(r) => r
          case Left(e) => throw e
        } )
      }
    }
    case AsTryAction(action) => mockRun(action) match {
      case Left(t) => Right(scala.util.Failure(t))
      case Right(r) => Right(scala.util.Success(r))
    }
    case x if x.isInstanceOf[SynchronousDatabaseAction[_, _, _, _]] => mockRun(x.nonFusedEquivalentAction)
    case FailedAction(actions) => throw new RuntimeException("FailedAction not implemented in mock")
    case NamedAction(actions, _) => throw new RuntimeException("NamedAction not implemented in mock")
    case CleanUpAction(actions, _, _, _) => throw new RuntimeException("ClenUpAction not implemented in mock")
    case x if x.isInstanceOf[DBIOAction[_, _, _]] => throw new RuntimeException(x.getDumpInfo.getNamePlusMainInfo)
    case x => if (x == null) throw new RuntimeException("Got null dbio") else throw new RuntimeException(x.getClass.toString)
  }

  def mockRunBlocking[T](repository:ValintarekisteriRepository): OngoingStubbing[Either[Throwable, T]] = {
    repository.runBlocking(any[DBIO[Any]], any[Duration]) answers (x => answerRun(x).fold(throw _, x => x))
    repository.runBlockingTransactionally(any[DBIO[Any]], any[Duration]) answers (x => answerRun(x))
  }
}
