import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.scaladsl.Flow

object MonitorStream {

  def countElements[A, B](monitoringActor: ActorRef,
                          message: B): Flow[A, A, NotUsed] =
    Flow[A]
      .map { item =>
        monitoringActor ! message
        item
      }

  def monitoring[A](monitor: A => Unit): Flow[A, A, NotUsed] = Flow[A].map {
    item =>
      monitor(item)
      item
  }

}
