package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

/**
 *
 */
case class List(
  id: Pk[Long],
  creatorId: Long,
  createdAt: Timestamp
)

object List extends Magic[List] with Timestamped[List] {
  def apply(creatorId: Long, createdAt: Timestamp) = {
    new List(NotAssigned, creatorId, createdAt)
  }
}
