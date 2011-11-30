package controllers

import java.util.Date
import models.{Account, User}
import play._
import play.cache.Cache
import play.data.validation.Validation
import play.mvc._
import scala.collection.JavaConversions._
import secure.NonSecure
import twitter4j.{User => TwUser}
import twitter4j.auth.{AccessToken => TwAccessToken}

object Users extends Controller with RenderCachedUser {
  import Constants.{TwAccessTokenCacheKey, TwUserObjCacheKey}
  import views.Users.html

  /**
   * Handles GET to signup page.
   */
  def neue() = Cache.get[TwAccessToken](TwAccessTokenCacheKey) match {
    case Some(twAccessToken) => {
      val (fullname, location, imgUrl) = Cache.get[TwUser](TwUserObjCacheKey) map { twUser =>
        (twUser.getName(), twUser.getLocation(), twUser.getProfileImageURL().toString)
      } getOrElse(("", "", ""))
      Cache.delete(TwUserObjCacheKey)
      html.neue(
        fullname,
        location,
        imgUrl,
        twAccessToken.getToken(),
        twAccessToken.getTokenSecret())
    }
    case None => html.neue()
  }

  /**
   * Handles POST of user creation.
   */
  def create(
    fullname: String,
    emailAddr: String,
    password: String
  ) = {
    Validation.required("fullname", fullname).message("Name is required")
    Validation.required("emailAddr", emailAddr).message("Email address is required")
    Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
    Validation.isTrue(
      "emailAddr",
      User.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
    ).message("Email address has already been registered")
    Validation.required("password", password).message("Password is required")

    if (Validation.hasErrors) {
      neue()
    } else {
      val (twToken, twSecret) = Cache.get[TwAccessToken](TwAccessTokenCacheKey) map { at =>
        (Some(at.getToken()), Some(at.getTokenSecret()))
      } getOrElse((None, None))
      Cache.delete(TwAccessTokenCacheKey)

      val user = User.create(User(emailAddr, password, fullname, twToken, twSecret))
//      Account.create(Account(user.id))
      // TODO: Action(Application.index)
    }
  }

  /**
   * Show a user's profile
   */
  def show(userId: Long) = {
    println(User.find("id = {userId}").on("userId" -> userId).first())
    User.find("id = {userId}").on("userId" -> userId).first() map {
      html.show(_)
    } getOrElse {
      NotFound("No such user")
    }
  }
}
