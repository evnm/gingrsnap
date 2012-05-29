package models

import controllers.Constants._
import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.channels.Channels
import java.sql.Timestamp
import play.cache.Cache
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._
import play.libs.Crypto
import twitter4j.{ProfileImage, TwitterFactory, User => TwitterUser}
import twitter4j.auth.AccessToken

case class GingrsnapUser(
  id: Pk[Long],
  emailAddr: Option[String],
  password: Option[String],
  salt: String,
  fullname: String,
  slug: String,
  createdAt: Timestamp,
  twUserId: Option[Long] = None,
  twUsername: Option[String] = None,
  twAccessToken: Option[String] = None,
  twAccessTokenSecret: Option[String] = None
)

/**
 * NOTE: email -> user is bijective.
 */
object GingrsnapUser extends Magic[GingrsnapUser] with Timestamped[GingrsnapUser] {
  /**
   * Returns whether or not a slug is unique across all extant user slugs.
   */
  def slugIsUniq(slug: String): Boolean = {
    GingrsnapUser.find("slug = {slug}")
      .on("slug" -> slug)
      .first()
      .isEmpty
  }

  /**
   * Returns a unique user slug, which is either the argument slug or
   * the argument slug with a numerical suffix.
   */
  def findUniqSlug(slug: String) = {
    if (slugIsUniq(slug))
      slug
    else {
      var suffix = 2
      while (!slugIsUniq(slug + suffix)) {
        suffix += 1
      }
      slug + suffix
    }
  }

  def apply(
    fullname: String,
    emailAddrOpt: Option[String],
    passwordOpt: Option[String],
    twUserIdOpt: Option[Long],
    twUsernameOpt: Option[String],
    twTokenOpt: Option[String],
    twSecretOpt: Option[String]
  ) = {
    val salt = scala.util.Random.nextInt.abs.toString
    val slug =
      if (twUsernameOpt.nonEmpty)
        findUniqSlug(twUsernameOpt.get.toLowerCase())
      else
        findUniqSlug(fullname.toLowerCase.replace(" ", "+"))

    new GingrsnapUser(
      NotAssigned,
      emailAddrOpt map { _.toLowerCase },
      passwordOpt map { password => Crypto.passwordHash(salt + password) },
      salt,
      fullname,
      slug,
      new Timestamp(System.currentTimeMillis()),
      twUserIdOpt,
      twUsernameOpt,
      twTokenOpt,
      twSecretOpt)
  }

  override def create(user: GingrsnapUser) = {
    super.create(user) map { createdUser =>
      val twUserOpt: Option[TwitterUser] = user.twUserId map { twUserId =>
        // Scrape the user's Twitter profile image.
        // TODO: What if they have a default Twitter avatar?
        val twitterIface = new TwitterFactory().getInstance()
        val twUser = twitterIface.showUser(twUserId)
        val twProfileImgUrl =
          new URL(twitterIface.getProfileImage(twUser.getScreenName(), ProfileImage.ORIGINAL).getURL)
        val twProfileImgPath = twProfileImgUrl.getFile()
        val (_, twProfileImgFilename) = twProfileImgPath.splitAt(twProfileImgPath.lastIndexOf("/"))
        val (filename, extension) = twProfileImgFilename.splitAt(twProfileImgFilename.lastIndexOf("."))
        val file = File.createTempFile(filename, extension)
        val byteChannel = Channels.newChannel(twProfileImgUrl.openStream())
        val outStream = new FileOutputStream(file)
        outStream.getChannel().transferFrom(byteChannel, 0, 1 << 24)
        outStream.close()

        Image.create(file) map { createdImage =>
          GingrsnapUserImage.create(GingrsnapUserImage(createdUser.id(), createdImage.id()))
        }
        file.delete()

        twUser
      }

      Account.create(
        Account(
          NotAssigned,
          createdUser.id(),
          twUserOpt map { _.getLocation },
          twUserOpt flatMap { twUser =>
            if (twUser.getURL != null) Some(twUser.getURL.toString) else None
          }))
      Cache.set(userIdCacheKey(createdUser.id()), createdUser, "6h")
      createdUser
    }
  }

  /**
   * Overridden update method to update cache as well as underlying store.
   */
  override def update(user: GingrsnapUser) = {
    Cache.set(userIdCacheKey(user.id()), user, "6h")
    super.update(user)
  }

  /**
   * Updates a user's passwords.
   */
  def updatePassword(user: GingrsnapUser, newPassword: String) = {
    GingrsnapUser.update(
      user.copy(password = Some(Crypto.passwordHash(user.salt + newPassword))))
  }

  /**
   * Validates a user against a password string.
   */
  def validatePassword(user: GingrsnapUser, password: String): Boolean = {
    user.password map { actualPassword =>
      actualPassword == Crypto.passwordHash(user.salt + password)
    } getOrElse(false)
  }

  protected[this] def userIdCacheKey(userId: Long) = GingrsnapUserObjKey + ":" + userId

  /**
   * Looks up a user by id. Caches and returns the looked-up user, if it exists.
   */
  def getById(userId: Long): Option[GingrsnapUser] = {
    Cache.get[GingrsnapUser](userIdCacheKey(userId)) orElse {
      GingrsnapUser.find("id = {userId}").on("userId" -> userId).first() map { user =>
        Cache.add(userIdCacheKey(userId), user, "6h")
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
          "6h")
        Cache.add(userIdCacheKey(user.id()), user, "6h")
        user
      }
    }
  }

  protected[this] def encryptedEmailToUserIdCacheKey(encryptedEmail: String) =
    EncryptedEmailToUserIdKey + ":" + encryptedEmail

  /**
   * Looks up a user by encrypted email address. Caches secondary index and
   * returns the looked-up user, if it exists.
   */
  def getByEncryptedEmail(encryptedEmail: String): Option[GingrsnapUser] = {
    Cache.get[java.lang.Long](encryptedEmailToUserIdCacheKey(encryptedEmail)) match {
      case Some(userId) => getById(userId.longValue)
      case None => getByEmail(Crypto.decryptAES(encryptedEmail)) map { user =>
        // Set encrypted email addr -> user id secondary index.
        Cache.add(
          encryptedEmailToUserIdCacheKey(encryptedEmail),
          java.lang.Long.valueOf(user.id()),
          "6h")
        Cache.add(userIdCacheKey(user.id()), user, "6h")
        user
      }
    }
  }

  protected[this] def encryptedTwTokenToUserIdCacheKey(encryptedTwToken: String) =
    EncryptedTwTokenToUserIdKey + ":" + encryptedTwToken

  /**
   * Looks up a user by encrypted Twitter auth token. Caches secondary index and
   * returns the looked-up user, if it exists.
   */
  def getByEncryptedTwToken(encryptedTwToken: String): Option[GingrsnapUser] = {
    Cache.get[java.lang.Long](encryptedTwTokenToUserIdCacheKey(encryptedTwToken)) match {
      case Some(userId) => getById(userId.longValue)
      case None => getByTwToken(Crypto.decryptAES(encryptedTwToken)) map { user =>
        // Set encrypted email addr -> user id secondary index.
        Cache.add(
          encryptedTwTokenToUserIdCacheKey(encryptedTwToken),
          java.lang.Long.valueOf(user.id()),
          "6h")
        Cache.add(userIdCacheKey(user.id()), user, "6h")
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
    GingrsnapUser.find("lower(emailAddr) = {e}").on("e" -> emailAddr).first()
  }

  /**
   * Looks up a user by email and verifies by password. Optionally returns
   * the looked-up user.
   */
  def getByEmailAndPass(emailAddr: String, password: String): Option[GingrsnapUser] = {
    getByEmail(emailAddr) match {
      case Some(user) if user.password map { actualPassword =>
        Crypto.passwordHash(user.salt + password) == actualPassword
      } getOrElse(false) => Some(user)

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

  /**
   * Looks up a user by Twitter access token.
   */
  def getByTwToken(token: String) = {
    GingrsnapUser.find("twAccessToken = {token}")
      .on("token" -> token)
      .first()
  }

  /**
   * Get the number of users that a given user follows.
   */
  def getFollowingCount(userId: Long) = {
    Follow.count("subjectId = {userId} and followType = {followType}")
      .on("userId" -> userId, "followType" -> FollowType.UserToUser.id)
      .single()
  }

  /**
   * Get the number of users that follow a given user.
   */
  def getFollowerCount(userId: Long) = {
    Follow.count("objectId = {userId} and followType = {followType}")
      .on("userId" -> userId, "followType" -> FollowType.UserToUser.id)
      .single()
  }

  /**
   * Get a Seq[GingrsnapUser] of those who a given user follows.
   */
  def getFollowing(userId: Long): Seq[GingrsnapUser] = {
    SQL("""
        select gu.* from GingrsnapUser gu
        join Follow f on f.objectId = gu.id
        where f.subjectId = {userId} and f.followType = {followType}
        """)
      .on("userId" -> userId, "followType" -> FollowType.UserToUser.id)
      .as(GingrsnapUser *)
  }

  /**
   * Get a Seq[GingrsnapUser] of a given user's followers.
   */
  def getFollowers(userId: Long): Seq[GingrsnapUser] = {
    SQL("""
        select gu.* from GingrsnapUser gu
        join Follow f on f.subjectId = gu.id
        where f.objectId = {userId} and f.followType = {followType}
        """)
      .on("userId" -> userId, "followType" -> FollowType.UserToUser.id)
      .as(GingrsnapUser *)
  }
}
