
import Processor.HttpResponseBody
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.ClientError
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, _}
import scala.util.{Failure, Success}


class Processor(monitoringActor: ActorRef, system: ActorSystem, materializer: ActorMaterializer) {

  implicit val sys = system
  implicit val mat = materializer
  implicit val ec = system.dispatcher

  def query(httpRequest: HttpRequest, request: Request): Future[(HttpResponseBody, Request)] = {
    val r = RestartSource
      .withBackoff(
        minBackoff = 10.milliseconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2,
        maxRestarts = 2
      ) { () =>
        val start = System.nanoTime()
        monitoringActor ! MonitoringActor.RequestStarted
        val responseFuture: Future[HttpResponse] =
          Http().singleRequest(httpRequest)

        Source
          .fromFuture(responseFuture)
          .mapAsync(parallelism = 1) { response =>
            val data =
              response.entity.toStrict(20.seconds).map(_.data.utf8String)

            val future = response.status match {
              case StatusCodes.OK =>
                data.map((_, request))
              case _: ClientError =>
                monitoringActor ! MonitoringActor.RequestFinished
                data
                  .flatMap(
                    msg =>
                      Future.failed(
                        new Exception(s"client side error: $msg")
                      )
                  )
              case statusCode: StatusCode =>
                monitoringActor ! MonitoringActor.RequestFinished
                data
                  .map(
                    response => throw new Exception(s"$statusCode: $response")
                  )
            }
            future.onComplete { t =>
              val duration =
                FiniteDuration.apply(System.nanoTime() - start, NANOSECONDS)
              t match {
                case Success(_) =>
                  monitoringActor ! MonitoringActor.RequestDuration(duration,
                    "success")
                case Failure(ex) =>
                  monitoringActor ! MonitoringActor.RequestDuration(duration,
                    "error")
              }
            }
            future
          }
      }
      .runWith(Sink.head)
    r.onComplete {
      case Success(_) =>
        monitoringActor ! MonitoringActor.RequestFinished
      case Failure(ex) =>
    }
    r
  }

}

object Processor {
  type HttpResponseBody = String
}
