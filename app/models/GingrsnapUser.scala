package models

import controllers.Constants.{EncryptedEmailToUserIdKey, GingrsnapUserObjKey}
import java.sql.Timestamp
import play.cache.Cache
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._
import play.libs.Crypto
import scala.reflect.Manifest
import twitter4j.auth.AccessToken

case class GingrsnapUser(
  id: Pk[Long],
  emailAddr: String,
  password: String,
  salt: String,
  fullname: String,
  createdAt: Timestamp,
  twAccessToken: Option[String] = None,
  twAccessTokenSecret: Option[String] = None
)

object EmptyGingrsnapUser extends GingrsnapUser(NotAssigned, "", "", "", "", null, None, None)

/**
 * NOTE: email -> user is bijective.
 */
object GingrsnapUser extends Magic[GingrsnapUser] {
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

  def apply(
    emailAddr: String,
    password: String,
    fullname: String,
    twToken: Option[String],
    twSecret: Option[String]
  ) = {
    val salt = scala.util.Random.nextInt.abs.toString
    new GingrsnapUser(
      NotAssigned,
      emailAddr,
      Crypto.passwordHash(salt + password),
      salt,
      fullname,
      new Timestamp(System.currentTimeMillis()),
      twToken,
      twSecret)
  }

  /**
   * Overridden update method to update cache as well as underlying store.
   */
  override def update(user: GingrsnapUser) = {
    Cache.set(userIdCacheKey(user.id()), user, "1h")
    super.update(user)
  }

  /**
   * Validates a user against a password string.
   */
  def validatePassword(user: GingrsnapUser, password: String): Boolean =
    user.password == Crypto.passwordHash(user.salt + password)

  protected[this] def userIdCacheKey(userId: Long) = GingrsnapUserObjKey + ":" + userId

  /**
   * Looks up a user by id. Caches and returns the looked-up user, if it exists.
   */
  def getById(userId: Long): Option[GingrsnapUser] = {
    Cache.get[GingrsnapUser](userIdCacheKey(userId)) orElse {
      GingrsnapUser.find("id = {userId}").on("userId" -> userId).first() map { user =>
        Cache.add(userIdCacheKey(userId), user, "1h")
        user
      }
    }
  }

  protected[this] def encryptedEmailToUserIdCacheKey(encryptedEmail: String) =
    EncryptedEmailToUserIdKey + ":" + encryptedEmail

  /**
   * Looks up a user by encrypted password. Caches secondary index and returns
   * the looked-up user, if it exists.
   */
  def getByEncryptedEmail(encryptedEmail: String) = {
    Cache.get[java.lang.Long](encryptedEmailToUserIdCacheKey(encryptedEmail)) match {
      case Some(userId) => getById(userId.longValue)
      case None => getByEmail(Crypto.decryptAES(encryptedEmail)) map { user =>
        // Set encrypted email addr -> user id secondary index.
        Cache.add(
          encryptedEmailToUserIdCacheKey(encryptedEmail),
          java.lang.Long.valueOf(user.id()),
          "1h")
        Cache.add(userIdCacheKey(user.id()), user, "1h")
        user
      }
    }
  }

  /**
   * Looks up a user by email. Optionally returns the looked-up user.
   *
   * NOTE: Does not verify password.
   */
  def getByEmail(emailAddr: String): Option[GingrsnapUser] = {
    GingrsnapUser.find("emailAddr = {e}").on("e" -> emailAddr).first()
  }

  /**
   * Looks up a user by email and verifies by password. Optionally returns
   * the looked-up user.
   */
  def getByEmailAndPass(emailAddr: String, password: String): Option[GingrsnapUser] = {
    getByEmail(emailAddr) match {
      case Some(user) if Crypto.passwordHash(user.salt + password) == user.password => Some(user)
      case _ => None
    }
  }

  /**
   * Looks up a user by Twitter access token and access token secret.
   */
  def getByTwAuth(token: AccessToken) = {
    GingrsnapUser.find("twAccessToken = {token} and twAccessTokenSecret = {secret}")
      .on("token" -> token.getToken, "secret" -> token.getTokenSecret)
      .first()
  }
}
