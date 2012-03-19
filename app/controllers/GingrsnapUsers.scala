package controllers

import models._
import notifiers.Mails
import play._
import play.cache.Cache
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._
import secure.{NonSecure, PasswordCredential, TwAuthCredential}
import twitter4j.TwitterFactory
import twitter4j.auth.AccessToken

object GingrsnapUsers extends BaseController with Secure {
  import Constants.TwIfaceCacheKey
  import views.GingrsnapUsers.html

  /**
   * User home page with feed of recipes by users followed on Gingrsnap.
   *
   * Invoking Application.index directly because Application.index and
   * GingrsnapUsers.home are mutually recursive.
   */
  def followingRecipes: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      if (Feature(Constants.UserFollowing)) {
        val connectedUserRecipes = Recipe.getAllByUserId(user.id()) map { recipe =>
          (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
        }
        val recipeFeed = Recipe.getMostRecentFollowed(user.id(), 20) map {
          Recipe.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithRecipes(
          user,
          connectedUserRecipes,
          RecipeFeedType.GingrsnapFollowing.id,
          recipeFeed)
      } else {
        GingrsnapUsers.globalRecipes
      }
    case None => Application.index
  }

  /**
   * User home page with feed of events related to all users.
   */
  def globalRecipes: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      val connectedUserRecipes = Recipe.getAllByUserId(user.id()) map { recipe =>
        (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
      }
      val recipeFeed = Recipe.getMostRecent(20) map {
        Recipe.hydrate(_)
      }
      views.GingrsnapUsers.html.homeWithRecipes(
        user,
        connectedUserRecipes,
        RecipeFeedType.Global.id,
        recipeFeed)
    case None => Application.index
  }

  /**
   * User home page with feed of events related to users followed on Twitter.
   */
  def twitterRecipes: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      if (Feature(Constants.TwitterEventFeeds)) {
        val connectedUserRecipes = Recipe.getAllByUserId(user.id()) map { recipe =>
          (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
        }
        val recipeFeed = Recipe.getMostRecentFollowed(user.id(), 20) map {
          Recipe.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithRecipes(
          user,
          connectedUserRecipes,
          RecipeFeedType.GingrsnapFollowing.id,
          recipeFeed)
      } else {
        GingrsnapUsers.globalRecipes
      }
    case None => Application.index
  }

  /**
   * User home page with feed of events related to users followed on Gingrsnap.
   *
   * Invoking Application.index directly because Application.index and
   * GingrsnapUsers.home are mutually recursive.
   */
  def followingEvents: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      if (Feature(Constants.UserFollowing)) {
        val recipesWithImages = Recipe.getAllByUserId(user.id()) map { recipe =>
          (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
       }
        val events = Event.getMostRecentFollowed(user.id(), 20) map {
          Event.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithEvents(
          user,
          recipesWithImages,
          EventFeedType.GingrsnapFollowing.id,
          events
        )
      } else {
        GingrsnapUsers.globalEvents
      }
    case None => Application.index
  }

  /**
   * User home page with feed of events related to all users.
   */
  def globalEvents: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      val recipesWithImages = Recipe.getAllByUserId(user.id()) map { recipe =>
        (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
      }
      val events = Event.getMostRecent(20).map(Event.hydrate)
      views.GingrsnapUsers.html.homeWithEvents(
        user,
        recipesWithImages,
        EventFeedType.Global.id,
        events)
    case None => Application.index
  }

  /**
   * User home page with feed of events related to users followed on Twitter.
   */
  def twitterEvents: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      if (Feature(Constants.TwitterEventFeeds)) {
        val recipesWithImages = Recipe.getAllByUserId(user.id()) map { recipe =>
          (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
        }
        val events = Event.getMostRecentFollowed(user.id(), 20) map {
          Event.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithEvents(
          user,
          recipesWithImages,
          EventFeedType.GingrsnapFollowing.id,
          events
        )
      } else {
        GingrsnapUsers.globalEvents
      }
    case None => Application.index
  }

  /**
   * Handles GET to signup page.
   */
  @NonSecure def neue(
    twUserId: Long = -1,
    twAccessToken: String = "",
    twAccessTokenSecret: String = ""
  ) = Authentication.getLoggedInUser match {
    case Some(_) => Action(Application.index)
    case None => {
      if (twUserId > 0 && twAccessToken.nonEmpty && twAccessTokenSecret.nonEmpty) {
        val accessToken = new AccessToken(twAccessToken, twAccessTokenSecret)

        // Log user in with a message if they're already registered.
        GingrsnapUser.getByTwAuth(accessToken) match {
          case Some(user) =>
            Authentication.authenticate(user.emailAddr, TwAuthCredential(accessToken))
            flash.success("You've already registered on Gingrsnap by connecting your Twitter account")
            Action(Application.index)
          case None => {
            val twitterIface = new TwitterFactory().getInstance()
            twitterIface.setOAuthAccessToken(accessToken)
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
          }
        }
      } else {
        html.neue()
      }
    }
  }

  /**
   * Handles POST of user creation.
   */
  @NonSecure def create(
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
        val twToken = if (twAccessToken.nonEmpty) Some(twAccessToken.split(", ")(0)) else None
        val twSecret = if (twAccessTokenSecret.nonEmpty) Some(twAccessTokenSecret.split(", ")(0)) else None
        // TODO: Why the fuck is the .split(", ")(0) necessary??

        GingrsnapUser.create(
          GingrsnapUser(emailAddr, password, fullname, twId, twToken, twSecret)
        ).toOptionLoggingError map { createdUser =>
          Authentication.authenticate(emailAddr, PasswordCredential(password))
          flash.success("Welcome to Gingrsnap, " + fullname)
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
  @NonSecure def show(userSlug: String) = GingrsnapUser.getBySlug(userSlug) map { user =>
    val eventFeed = Event.getMostRecentByUserId(user.id(), 10) map { e =>
      Event.hydrate(e)
    }
    val connectedUser = Authentication.getLoggedInUser
    val isFollowedByConnectedUser = (connectedUser map { connectedUser =>
      Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
    }).getOrElse(false)
    val recipesWithImages: Seq[(Recipe, Option[(String, String)])] =
      (if (connectedUser.nonEmpty && user.id() == connectedUser.get.id()) {
        Recipe.getAllByUserId(user.id())
      } else {
        Recipe.getPublishedByUserId(user.id())
      }) map { recipe =>
        (recipe, Image.getBaseUrlByRecipeId(recipe.id()))
      }

    html.show(
      user,
      connectedUser,
      isFollowedByConnectedUser,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      recipesWithImages,
      Make.getCountByUserId(user.id()),
      eventFeed)
  } getOrElse {
    NotFound("No such user")
  }
}
