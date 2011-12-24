package models

import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

object Account extends Magic[Account] {
  def apply(userId: Long) = new Account(NotAssigned, userId, None)

  /**
   * Get an account object by the associated user's id.
   */
  def getByUserId(userId: Long) =
    Account.find("userId = {userId}").on("userId" -> userId).first()
}

case class Account(
  id: Pk[Long],
  userId: Long,
  location: Option[String] = None,
  url: Option[String] = None
)
