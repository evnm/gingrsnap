package models

import controllers.Constants.AccountObjKey
import java.io.File
import java.sql.Timestamp
import java.util.UUID
import notifiers.Mails
import play.cache.Cache
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr

case class Account(
  id: Pk[Long],
  userId: Long,
  location: Option[String] = None,
  url: Option[String] = None
)

object Account extends Magic[Account] {
  protected[this] def accountCacheKey(userId: Long) = AccountObjKey + ":" + userId

  override def create(account: Account) = {
    super.create(account) map { createdAccount =>
      Cache.set(accountCacheKey(createdAccount.userId), createdAccount, "6h")
      createdAccount
    }
  }

  /**
   * Update an account, optionally replacing its associated image.
   */
  def update(
    account: Account,
    imageOpt: Option[File] = None
  ): Unit = {
    Cache.set(accountCacheKey(account.userId), account, "6h")

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
   * Creates a PasswordResetRequest and emails the user.
   */
  def passwordReset(user: GingrsnapUser): MayErr[SqlRequestError, PasswordResetRequest] = {
    val pwdResetRequest = PasswordResetRequest(
      Id(UUID.randomUUID().toString),
      user.id(),
      new Timestamp(System.currentTimeMillis()),
      false)
    PasswordResetRequest.insert(pwdResetRequest) map { _ =>
      val confirmationUrl = "%saccount/password_reset/%s".format(
        play.configuration("application.baseUrl"),
        pwdResetRequest.id())
      Mails.resetPassword(user.emailAddr.get, user.fullname, confirmationUrl)
      pwdResetRequest
    }
  }

  /**
   * Get an account object by the associated user's id.
   */
  def getByGingrsnapUserId(userId: Long) = {
    Cache.get[Account](accountCacheKey(userId)) orElse {
      Account.find("userId = {userId}").on("userId" -> userId).first() map { account =>
        Cache.add(accountCacheKey(userId), account, "6h")
        account
      }
    }
  }
}
