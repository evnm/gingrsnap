package models

import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

object Account extends Magic[Account] {
  def apply(userId: Long) = new Account(NotAssigned, userId, None)
}

case class Account(
  id: Pk[Long],
  userId: Long,
  location: Option[String] = None
)
