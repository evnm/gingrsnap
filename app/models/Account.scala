package models

import java.io.File
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

case class Account(
  id: Pk[Long],
  userId: Long,
  location: Option[String] = None,
  url: Option[String] = None
)

object Account extends Magic[Account] {
  /**
   * Get an account object by the associated user's id.
   */
  def getByGingrsnapUserId(userId: Long) =
    Account.find("userId = {userId}").on("userId" -> userId).first()

  /**
   * Update an account, optionally replacing its associated image.
   */
  def update(
    account: Account,
    imageOpt: Option[File] = None
  ): Unit = {
    imageOpt map { imageFile =>
      // Delete old image if one exists.
      Image.getByUserId(account.userId) map { oldImage =>
        Image.delete(oldImage)
        GingrsnapUserImage.delete(account.userId, oldImage.id())
      }
      Image.create(imageFile) map { createdImage =>
        GingrsnapUserImage.create(GingrsnapUserImage(account.userId, createdImage.id()))
      }
    }

    Account.update(account)
  }
}
