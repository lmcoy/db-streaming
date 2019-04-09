import java.sql.Date

import slick.jdbc.GetResult

import scala.annotation.tailrec

object SQLSupport {

  trait SQLType

  object SQLType {
    def string[A](s: Option[A], f: A => String = { a: A =>
      s"$a"
    }): String = s match {
      case None    => "null"
      case Some(v) => f(v)
    }
  }

  case class SQLInteger(i: Option[Int]) extends SQLType {
    override def toString: String = SQLType.string(i)
  }
  case class SQLString(s: Option[String]) extends SQLType {
    override def toString: String = SQLType.string(s, (a: String) => s"'$a'")
  }
  case class SQLDate(date: Option[Date]) extends SQLType {
    override def toString: String = SQLType.string(date)
  }

  // row in a table
  case class Row(columns: Map[String, SQLType])

  // convert sql to Row
  implicit val getResultRow: GetResult[Row] = GetResult[Row] { v1 =>
    def getValue(column: Int) = {
      v1.rs.getMetaData.getColumnName(column) -> {
        v1.rs.getMetaData.getColumnType(column) match {
          case java.sql.Types.INTEGER => SQLInteger(v1.nextIntOption())
          case java.sql.Types.VARCHAR => SQLString(v1.nextStringOption())
          case java.sql.Types.DATE    => SQLDate(v1.nextDateOption())
        }
      }
    }

    @tailrec
    def go(column: Int, acc: Map[String, SQLType]): Map[String, SQLType] = {
      if (column > v1.numColumns) acc
      else go(column + 1, acc + getValue(column))
    }
    Row(go(1, Map()))
  }

}
