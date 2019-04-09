import SQLSupport._
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class Request(rows: Seq[Row])

object DBApp extends App {
  val startTime = System.nanoTime()

  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val session = SlickSession.forConfig("slick-postgres")

  import session.profile.api._

  val monitoringActor = system.actorOf(MonitoringActor.props(), "monitoring")

  val processor = new Processor(monitoringActor, system, mat)

  def requestToHttp(uri: Uri) = Flow[Request].map { request =>
    val body = HttpEntity.apply(ContentTypes.`application/json`,
                                JsonSupport.rowAsJson(request.rows))
    (HttpRequest(HttpMethods.POST, uri, entity = body), request)
  }

  val prepareRequest: Flow[Row, Request, NotUsed] = Flow[Row]
    .groupedWithin(200, 5.seconds)
    .map(rows => Request(rows))

  val sendRequest: Flow[Request, Seq[Row], NotUsed] = Flow[Request]
    .via(requestToHttp(Uri("http://localhost:8080/answer")))
    .mapAsync(16)(r => processor.query(r._1, r._2))
    .via(JsonSupport.jsonResponseToRow)

  val sinkDB = Flow[Row]
    .grouped(200)
    .toMat(Slick.sink(
      parallelism = 4,
      (group: Seq[Row]) => {

        val values = group
          .map(row => row.columns.values.mkString("(", ",", ")"))
          .mkString(",")
        val columns = group.head.columns.keys.mkString("(", ",", ")")

        sqlu"""INSERT INTO tbl #$columns VALUES #$values"""
      }
    ))(Keep.right)

  val enrichRow: Flow[Row, Row, NotUsed] =
    Flow[Row]
      .via(prepareRequest)
      .via(sendRequest)
      .via(MonitorStream.monitoring(elem =>
        monitoringActor ! MonitoringActor.ProcessedRows(elem.size)))
      .mapConcat[Row](x => identity(x).toList)

  val stream = Slick
    .source(sql"SELECT * FROM flights".as[Row])
    .via(MonitorStream.countElements(monitoringActor, MonitoringActor.ReadLine))
    .via(enrichRow)
    .runWith(sinkDB)

  val routes = path("test") {
    get {
      val x =
        (monitoringActor ? MonitoringActor.Metrics)(10.seconds).mapTo[String]

      complete(x)
    }
  }
  val bindingFuture = Http().bindAndHandle(routes, "localhost", 8099)

  system.registerOnTermination(() => session.close())

  stream.onComplete { t =>
    t match {
      case Success(value) =>
      case Failure(ex) =>
        println(s"failure: ${ex.getMessage}")

    }
    val duration =
      FiniteDuration.apply(System.nanoTime() - startTime, NANOSECONDS)
    println(s"runtime: ${duration.toSeconds} s")
    system.terminate()
  }
}
