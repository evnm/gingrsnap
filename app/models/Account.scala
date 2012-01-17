package models

import controllers.Constants.AccountObjKey
import java.io.File
import play.cache.Cache
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
  protected[this] def accountCacheKey(userId: Long) = AccountObjKey + ":" + userId

  override def create(account: Account) = {
    Cache.set(accountCacheKey(account.userId), account, "1h")
    super.create(account)
  }

  /**
   * Update an account, optionally replacing its associated image.
   */
  def update(
    account: Account,
    imageOpt: Option[File] = None
  ): Unit = {
    Cache.set(accountCacheKey(account.userId), account, "1h")

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

  /**
   * Get an account object by the associated user's id.
   */
  def getByGingrsnapUserId(userId: Long) = {
    Cache.get[Account](accountCacheKey(userId)) orElse {
      Account.find("userId = {userId}").on("userId" -> userId).first() map { account =>
        Cache.add(accountCacheKey(userId), account, "1h")
        account
      }
    }
  }
}
