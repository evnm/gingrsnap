package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class PasswordResetRequest(
  id: Pk[String],
  userId: Long,
  createdAt: Timestamp,
  resetCompleted: Boolean
)

object PasswordResetRequest
  extends Magic[PasswordResetRequest] with Timestamped[PasswordResetRequest]
{
  /**
   * Returns true if the request is capable of beign completed, i.e. the request
   * was made <= 24 hours ago.
   */
  def isCompletable(pwdResetRequest: PasswordResetRequest): Boolean = {
    val now = System.currentTimeMillis()
    // 86400000 ms == 24 hours
    !pwdResetRequest.resetCompleted && (now - pwdResetRequest.createdAt.getTime < 86400000)
  }

  def getMostRecentByUserId(userId: Long): Option[PasswordResetRequest] = {
    SQL("""
        select * from PasswordResetRequest
        where userId = {userId}
        order by createdAt desc
        limit 1
        """)
      .on("userId" -> userId)
      .as(PasswordResetRequest ?)
  }

  def getById(uuid: String): Option[PasswordResetRequest] =
    PasswordResetRequest.find("id = {uuid}").on("uuid" -> uuid).first()
}
