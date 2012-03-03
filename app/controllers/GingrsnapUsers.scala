package controllers

import models._
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
   * User home page.
   *
   * Invoking Application.index directly because Application.index and
   * GingrsnapUsers.home are mutually recursive.
   */
  def home: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) => {
      val events = (
        if (Feature(Constants.UserFollowing)) {
          Event.getMostRecentFollowed(user.id(), 20)
        } else {
          Event.getMostRecent(20)
        }).map {
          Event.hydrate(_)
        }

      views.GingrsnapUsers.html.home(
        user,
        Recipe.getByUserId(user.id()),
        events
      )
    }
    case None => Application.index
  }

  /**
   * Handles GET to signup page.
   */
  def neue(
    twUserId: Long = -1,
    twAccessToken: String = "",
    twAccessTokenSecret: String = ""
  ) = Authentication.getLoggedInUser match {
    case Some(_) => Action(Application.index)
    case None => {
      if (twUserId > 0 && twAccessToken.nonEmpty && twAccessTokenSecret.nonEmpty) {
        val twitterIface = new TwitterFactory().getInstance()
        twitterIface.setOAuthAccessToken(new AccessToken(twAccessToken, twAccessTokenSecret))
        val twUser = twitterIface.verifyCredentials()
        val (fullname, location, imgUrl) =
          (twUser.getName(), twUser.getLocation(), twUser.getProfileImageURL().toString)

          html.neue(
            Some(fullname),
            Some(location),
            Some(imgUrl),
            Some(twUserId),
            Some(twAccessToken),
            Some(twAccessTokenSecret))
      } else {
        html.neue()
      }
    }
  }

  /**
   * Handles POST of user creation.
   */
  def create(
    fullname: String,
    emailAddr: String,
    password: String,
    twUserId: Long = -1,
    twAccessToken: String = "",
    twAccessTokenSecret: String = ""
  ) = Authentication.getLoggedInUser match {
    case Some(_) => Action(Application.index)
    case None => {
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
        val twId = if (twUserId > 0) Some(twUserId) else None
        val twToken = if (twAccessToken.nonEmpty) Some(twAccessToken) else None
        val twSecret = if (twAccessTokenSecret.nonEmpty) Some(twAccessTokenSecret) else None

        GingrsnapUser.create(
          GingrsnapUser(emailAddr, password, fullname, twId, twToken, twSecret)
        ).toOptionLoggingError map { createdUser =>
          Authentication.authenticate(emailAddr, PasswordCredential(password))
          flash.success("Successfully created your account! Welcome to Gingrsnap, " + fullname + ".")
          Mails.welcome(createdUser)
          Action(Application.index)
        } getOrElse {
          flash.error("Unfortunately, there was an error while creating your account")
          Action(Application.index)
        }
      }
    }
  }

  /**
   * Show a user's profile
   */
  def show(userSlug: String) = GingrsnapUser.getBySlug(userSlug) map { user =>
    val eventFeed = Event.getMostRecentByUserId(user.id(), 10) map { e =>
      Event.hydrate(e)
    }
    val connectedUser = Authentication.getLoggedInUser
    val isFollowedByConnectedUser = (connectedUser map { connectedUser =>
      Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
    }).getOrElse(false)
    html.show(
      user,
      connectedUser,
      isFollowedByConnectedUser,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      Recipe.getByUserId(user.id()),
      Make.getCountByUserId(user.id()),
      eventFeed)
  } getOrElse {
    NotFound("No such user")
  }
}
