package controllers

import java.util.Date
import models.{Account, Image, Make, Recipe, GingrsnapUser}
import notifiers.Mails
import play._
import play.cache.Cache
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._
import secure.{PasswordCredential, Security}
import twitter4j.{User => TwUser}
import twitter4j.auth.{AccessToken => TwAccessToken}

object GingrsnapUsers extends BaseController {
  import Constants.{TwAccessTokenCacheKey, TwUserObjCacheKey}
  import views.GingrsnapUsers.html

  /**
   * Handles GET to signup page.
   */
  def neue() = Cache.get[TwAccessToken](OAuth.mkCacheKey(TwAccessTokenCacheKey)) match {
    case Some(twAccessToken) => {
      val (fullname, location, imgUrl) = Cache.get[TwUser](
        OAuth.mkCacheKey(TwUserObjCacheKey)
      ) map { twGingrsnapUser =>
        (twGingrsnapUser.getName(), twGingrsnapUser.getLocation(), twGingrsnapUser.getProfileImageURL().toString)
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
    Validation.isTrue(
      "fullname",
      fullname.matches("[a-zA-Z]+( [a-zA-z]+)*")
    ).message("Full name must contain only letters and spaces")
    Validation.required("emailAddr", emailAddr).message("Email address is required")
    Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
    Validation.isTrue(
      "emailAddr",
      GingrsnapUser.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
    ).message("Email address has already been registered")
    Validation.required("password", password).message("Password is required")

    if (Validation.hasErrors) {
      neue()
    } else {
      val (twToken, twSecret) = Cache.get[TwAccessToken](
        OAuth.mkCacheKey(TwAccessTokenCacheKey)
      ) map { at =>
        (Some(at.getToken()), Some(at.getTokenSecret()))
      } getOrElse((None, None))
      Cache.delete(OAuth.mkCacheKey(TwAccessTokenCacheKey))
      Cache.delete(OAuth.mkCacheKey(TwUserObjCacheKey))

      GingrsnapUser.create(
        GingrsnapUser(emailAddr, password, fullname, twToken, twSecret)
      ).toOptionLoggingError map { createdUser =>
        Authentication.authenticate(emailAddr, PasswordCredential(password))
        flash.success("Successfully created your account! Welcome to Gingrsnap, " + fullname + ".")
        Mails.welcome(createdUser)
        Action(Application.index)
      } getOrElse {
        flash.error(
          "Unfortunately, there was an error while creating your account. Please try again.")
        Action(Application.index)
      }
    }
  }

  /**
   * Show a user's profile
   */
  def show(userSlug: String) = GingrsnapUser.getBySlug(userSlug) map { user =>
    val (publishedRecipes, drafts) = Recipe.getByUserId(user.id()).partition { recipe =>
      recipe.publishedAt.isDefined
    }
    html.show(
      user,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      publishedRecipes,
      drafts,
      Make.getCountByUserId(user.id()))
  } getOrElse {
    NotFound("No such user")
  }
}
