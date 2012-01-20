package models

import controllers.Constants.{EncryptedEmailToUserIdKey, GingrsnapUserObjKey, SlugToUserIdKey}
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
  slug: String,
  createdAt: Timestamp,
  twAccessToken: Option[String] = None,
  twAccessTokenSecret: Option[String] = None
)

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

  /**
   * Returns whether or not a slug is unique across all extant user slugs.
   */
  def slugIsUnique(slug: String): Boolean = {
    GingrsnapUser.find("slug = {slug}")
      .on("slug" -> slug)
      .first()
      .isEmpty
  }

  /**
   * Returns a unique user slug, which is either the argument slug or
   * the argument slug with a numerical suffix.
   */
  def findUniqueSlug(slug: String) = {
    if (slugIsUnique(slug))
      slug
    else {
      var suffix = 0
      while (!slugIsUnique(slug + suffix)) {
        suffix += 1
      }
      slug + suffix
    }
  }

  def apply(
    emailAddr: String,
    password: String,
    fullname: String,
    twToken: Option[String],
    twSecret: Option[String]
  ) = {
    val salt = scala.util.Random.nextInt.abs.toString
    val slug = findUniqueSlug(fullname.toLowerCase().replace(" ", "+"))

    new GingrsnapUser(
      NotAssigned,
      emailAddr,
      Crypto.passwordHash(salt + password),
      salt,
      fullname,
      slug,
      new Timestamp(System.currentTimeMillis()),
      twToken,
      twSecret)
  }

  override def create(user: GingrsnapUser) = {
    val result = super.create(user)
    result map { createdUser =>
      Account.create(Account(NotAssigned, createdUser.id()))
      Cache.set(userIdCacheKey(createdUser.id()), createdUser, "1h")
      createdUser
    }
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

  protected[this] def slugToUserIdCacheKey(userSlug: String) = SlugToUserIdKey + ":" + userSlug

  /**
   * Looks up a user by URL slug. Caches and returns the looked-up user, if it exists.
   */
  def getBySlug(userSlug: String): Option[GingrsnapUser] = {
    lazy val fromDb = GingrsnapUser.find("slug = {slug}").on("slug" -> userSlug).first()

    Cache.get[java.lang.Long](slugToUserIdCacheKey(userSlug)) match {
      case Some(userId) => getById(userId.longValue)
      case None => fromDb map { user =>
        // Set encrypted email addr -> user id secondary index.
        Cache.add(
          slugToUserIdCacheKey(userSlug),
          java.lang.Long.valueOf(user.id()),
          "1h")
        Cache.add(userIdCacheKey(user.id()), user, "1h")
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
  def getByEncryptedEmail(encryptedEmail: String): Option[GingrsnapUser] = {
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
