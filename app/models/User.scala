package models

import java.util.Date
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._
import play.libs.Crypto

object EmptyUser extends User(NotAssigned, "", "", "", "", null, None, None)

/**
 * NOTE: email -> user is bijective.
*/
object User extends Magic[User] {
  def apply(
    emailAddr: String,
    password: String,
    fullname: String,
    twToken: Option[String],
    twSecret: Option[String]
  ) = {
    val salt = scala.util.Random.nextInt.abs.toString
    new User(
      NotAssigned,
      emailAddr,
      Crypto.passwordHash(salt + password),
      salt,
      fullname,
      new Date(),
      twToken,
      twSecret)
  }

  /**
   * Looks up a user by email. Optionally returns the looked-up user.
   *
   * NOTE: Does not verify password.
   */
  def getByEmail(emailAddr: String): Option[User] = {
    User.find("emailAddr = {e}").on("e" -> emailAddr).first()
  }

  /**
   * Looks up a user by email and verifies by password. Optionally returns
   * the looked-up user.
   */
  def getByEmailAndPass(emailAddr: String, password: String): Option[User] = {
    getByEmail(emailAddr) match {
      case Some(user) if Crypto.passwordHash(user.salt + password) == user.password => Some(user)
      case _ => None
    }
  }
}

case class User(
  id: Pk[Long],
  emailAddr: String,
  password: String,
  salt: String,
  fullname: String,
  createdAt: Date,
  twAccessToken: Option[String] = None,
  twAccessTokenSecret: Option[String] = None
)
