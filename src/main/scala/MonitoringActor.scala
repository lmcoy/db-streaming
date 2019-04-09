import java.io.StringWriter
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.event.Logging
import io.prometheus.client._
import io.prometheus.client.exporter.common.TextFormat

import scala.concurrent.duration.FiniteDuration

class MonitoringActor extends Actor {
  import MonitoringActor._

  val log = Logging(context.system, this)

  val inProgressRequests: Gauge = Gauge
    .build()
    .name("inprogress_requests")
    .help("Inprogress requests.")
    .register()

  val responseTime: Histogram = Histogram
    .build()
    .name("response_time")
    .help("response time in ms")
    .labelNames("status")
    .buckets(5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
    .register()

  val loadedRows: Counter =Counter.build().name("loaded_rows")
    .help("number of loaded rows from DB")
    .register()

  val processedRows: Counter =Counter.build().name("processed_rows")
    .help("number of processed rows")
    .register()

  def receive: PartialFunction[Any, Unit] = {
    case RequestStarted  => inProgressRequests.inc()
    case RequestFinished => inProgressRequests.dec()
    case RequestDuration(duration, status) =>
      responseTime
        .labels(status)
        .observe(duration.toUnit(TimeUnit.MILLISECONDS))
    case Metrics =>
      sender ! {
        val writer = new StringWriter()
        TextFormat.write004(
          writer,
          CollectorRegistry.defaultRegistry.metricFamilySamples())
        writer.toString
      }
    case ReadLine => loadedRows.inc()
    case ProcessedRows(rows) => processedRows.inc(rows)
    case _ ⇒ log.info("received unknown message")
  }

}

object MonitoringActor {

  def props(): Props = Props(new MonitoringActor)

  case object Metrics

  case class Request(n: Int)

  case object ReadLine

  case object RequestStarted
  case object RequestFinished
  case object RequestFailed
  case class RequestDuration(duration: FiniteDuration, status: String)

  case class ProcessedRows(rows: Int)
}
