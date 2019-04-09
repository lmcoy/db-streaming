import Processor.HttpResponseBody
import SQLSupport.{Row, SQLInteger, SQLString}
import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.circe._
import io.circe.parser.parse
import io.circe.syntax._

object JsonSupport {

  def none[A](option: Option[A])(notNull: A => Json,
                                 nullValue: => Json = Json.fromString("null")): Json =
    option match {
      case Some(a) => notNull(a)
      case None    => nullValue
    }

  implicit val rowEncoder: Encoder[Row] = (a: Row) => {
    val columns = a.columns.map {
      case (column, SQLInteger(i)) => (column, none(i)(Json.fromInt))
      case (column, SQLString(s))  => (column, none(s)(Json.fromString))
    }
    Json.obj(columns.toSeq: _*)
  }

  implicit val seqRowEncoder: Encoder[Seq[Row]] = new Encoder[Seq[Row]] {
    override def apply(a: Seq[Row]): Json = {
      Json.arr(a.map(_.asJson): _*)
    }
  }

  val jsonResponseToRow: Flow[(HttpResponseBody, Request), Seq[Row], NotUsed] =
    Flow[(HttpResponseBody, Request)].map { x =>
      val maybeListJsonElements = for {
        json <- parse(x._1)
        arr <- json.asArray.toRight("not an array")
      } yield arr
      val listJsonElements = maybeListJsonElements match {
        case Left(msg) => throw new Exception(msg.toString)
        case Right(c)  => c.map(d => d.noSpaces)
      }
      if (x._2.rows.size == listJsonElements.size)
        x._2.rows.zip(listJsonElements).map {
          case (row, result) =>
            Row(row.columns ++ Map("result" -> SQLString(Some(result))))
        } else throw new Exception("length does not match")
    }

  def rowAsJson(rows: Seq[Row]): String = rows.asJson.noSpaces
}
