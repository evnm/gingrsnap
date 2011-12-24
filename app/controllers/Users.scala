package controllers

import java.util.Date
import models.{Account, Recipe, User}
import play._
import play.cache.Cache
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._
import secure.{PasswordCredential, Security}
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

      html.neue(fullname, location, imgUrl,
                twAccessToken.getToken(), twAccessToken.getTokenSecret())
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
      Cache.delete(TwUserObjCacheKey)

      User.create(User(emailAddr, password, fullname, twToken, twSecret)).e match {
        case Right(user) => {
          Account.create(Account(user.id()))
          Authentication.authenticate(emailAddr, PasswordCredential(password))
          Action(Application.index)
        }
        case Left(error) => {
          println("Error during creation of user, emailAddr(%s),password(%s),fullname(%s): %s",
                  emailAddr, password, fullname, error)
          flash.error("Unfortunately, there was an error while creating your account. Please try again.")
        }
      }
    }
  }

  /**
   * Show a user's profile
   */
  def show(userId: Long) = User.getById(userId) map { user =>
    html.show(user, Account.getByUserId(user.id()).get, Recipe.getByUserId(user.id()))
  } getOrElse {
    NotFound("No such user")
  }
}
