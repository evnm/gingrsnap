package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults.Magic
import scala.reflect.Manifest

/**
 * Utterly-jank solution to Play + Postgres blowing up on java.sql.Timestamps.
 */
trait Timestamped[T] { this: Magic[T] =>
  override def extendExtractor[C](f:(Manifest[C] =>
    Option[ColumnTo[C]]), ma:Manifest[C]):Option[ColumnTo[C]] = (ma match {
    case m if m == Manifest.classType(classOf[Timestamp]) =>
      Some(rowToTimestamp)
    case _ => None
  }).asInstanceOf[Option[ColumnTo[C]]]

  def rowToTimestamp: Column[Timestamp] = {
    Column[Timestamp](transformer = { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case time:java.sql.Timestamp => Right(time)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value + " to Timestamp for column " + qualified))
      }
    })
  }
}
