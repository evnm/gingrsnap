package controllers

import java.util.Date
import models.{Account, Event, Image, Make, Recipe, GingrsnapUser}
import notifiers.Mails
import play._
import play.cache.Cache
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._
import secure.PasswordCredential
import twitter4j.TwitterFactory
import twitter4j.auth.AccessToken

object GingrsnapUsers extends BaseController {
  import Constants.TwIfaceCacheKey
  import views.GingrsnapUsers.html

  /**
   * Handles GET to signup page.
   */
  def neue(twAccessToken: String = "", twAccessTokenSecret: String = "") = {
    if (twAccessToken.nonEmpty && twAccessTokenSecret.nonEmpty) {
      val twitterIface = new TwitterFactory().getInstance()
      twitterIface.setOAuthAccessToken(new AccessToken(twAccessToken, twAccessTokenSecret))
      val twUser = twitterIface.verifyCredentials()
      val (fullname, location, imgUrl) =
        (twUser.getName(), twUser.getLocation(), twUser.getProfileImageURL().toString)

      html.neue(fullname, location, imgUrl, twAccessToken, twAccessTokenSecret)
    } else {
      html.neue()
    }
  }

  /**
   * Handles POST of user creation.
   */
  def create(
    fullname: String,
    emailAddr: String,
    password: String,
    twAccessToken: String = "",
    twAccessTokenSecret: String = ""
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
      val twToken = if (twAccessToken.nonEmpty) Some(twAccessToken) else None
      val twSecret = if (twAccessTokenSecret.nonEmpty) Some(twAccessTokenSecret) else None

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
    val eventFeed = Event.getMostRecentByUserId(user.id(), 20) map { e =>
      Event.hydrate(e)
    }
    html.show(
      user,
      Authentication.getLoggedInUser,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      Recipe.getByUserId(user.id()),
      Make.getCountByUserId(user.id()),
      eventFeed)
  } getOrElse {
    NotFound("No such user")
  }
}
