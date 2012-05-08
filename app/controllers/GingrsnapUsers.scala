package controllers

import models._
import notifiers.Mails
import play._
import play.cache.Cache
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._
import secure.{NonSecure, PasswordCredential, TwAuthCredential}
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
        val connectedUserRecipes = Recipe.getAllByUserId(user.id())
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
      val connectedUserRecipes = Recipe.getAllByUserId(user.id())
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
        val connectedUserRecipes = Recipe.getAllByUserId(user.id())
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
        val connectedUserRecipes = Recipe.getAllByUserId(user.id())
        val events = Event.getMostRecentFollowed(user.id(), 20) map {
          Event.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithEvents(
          user,
          connectedUserRecipes,
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
      val connectedUserRecipes = Recipe.getAllByUserId(user.id())
      val events = Event.getMostRecent(20).map(Event.hydrate)
      views.GingrsnapUsers.html.homeWithEvents(
        user,
        connectedUserRecipes,
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
        val connectedUserRecipes = Recipe.getAllByUserId(user.id())
        val events = Event.getMostRecentFollowed(user.id(), 20) map {
          Event.hydrate(_)
        }

        views.GingrsnapUsers.html.homeWithEvents(
          user,
          connectedUserRecipes,
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
    fullnameOpt: Option[String] = None,
    emailAddrOpt: Option[String] = None
  ) = Authentication.getLoggedInUser match {
    case Some(_) => Action(Application.index)
    case None => html.neue(fullnameOpt, emailAddrOpt)
  }

  /**
   * Handles POST of user creation.
   */
  @NonSecure def create(
    fullname: String,
    location: String = "",
    url: String = "",
    emailAddr: String = "",
    password: String = "",
    twUserId: Long = -1,
    twUsername: String = "",
    twAccessToken: String = "",
    twAccessTokenSecret: String = ""
  ) = Authentication.getLoggedInUser match {
    case Some(_) => Action(Application.index)
    case None => {
      Validation.required("fullname", fullname).message("Name is required")
      if (emailAddr.nonEmpty) {
        Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
        Validation.isTrue(
          "emailAddr",
          GingrsnapUser.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
        ).message("Email address has already been registered")
        Validation.required("password", password)
          .message("Password is required when email address is provided")
      }

      Validation.isTrue(
        "auth_required",
        (emailAddr.nonEmpty && password.nonEmpty)
          || (twUserId > 0 && twUsername.nonEmpty && twAccessToken.nonEmpty && twAccessTokenSecret.nonEmpty)
      ).message("User can't be created without either email+password or Twitter auth credentials.")

      if (url.nonEmpty) {
        Validation.url("url", url).message("Provided website URL must be a valid URL")
      }

      if (Validation.hasErrors) {
        neue(Some(fullname), Some(emailAddr))
      } else {
        val emailAddrOpt = if (emailAddr.nonEmpty) Some(emailAddr) else None
        val passwordOpt = if (password.nonEmpty) Some(password) else None
        val twIdOpt = if (twUserId > 0) Some(twUserId) else None
        val twUsernameOpt = if (twUsername.nonEmpty) Some(twUsername) else None
        val twTokenOpt = if (twAccessToken.nonEmpty) Some(twAccessToken.split(", ")(0)) else None
        val twSecretOpt =
          if (twAccessTokenSecret.nonEmpty) Some(twAccessTokenSecret.split(", ")(0)) else None
        // TODO: Why the fuck is the .split(", ")(0) necessary??

        GingrsnapUser.create(
          GingrsnapUser(fullname, emailAddrOpt, passwordOpt, twIdOpt, twUsernameOpt, twTokenOpt, twSecretOpt)
        ).toOptionLoggingError map { createdUser =>
          if (emailAddr.nonEmpty) {
            Mails.welcome(createdUser)
            Authentication.authenticate(emailAddr, PasswordCredential(password))
          } else {
            Authentication.authenticate(
              twAccessToken,
              TwAuthCredential(new AccessToken(twAccessToken, twAccessTokenSecret)))
          }

          flash.success("Welcome to Gingrsnap, " + fullname)
          Action(Application.index)
        } getOrElse {
          flash.error("Unfortunately, there was an error while creating your account")
          Action(Application.index)
        }
      }
    }
  }

  /**
   * Default show method is showWithRecipes.
   */
  @NonSecure def show(userSlug: String) = showWithRecipes(userSlug)

  /**
   * Show a user's profile with a feed of their recipes.
   */
  @NonSecure def showWithRecipes(userSlug: String) = GingrsnapUser.getBySlug(userSlug) map { user =>
    val recipeFeed = Recipe.getPublishedByUserId(user.id()) map {
      Recipe.hydrate(_)
    }
    val connectedUserOpt = Authentication.getLoggedInUser
    val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
      Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
    }).getOrElse(false)
    val drafts: Seq[Recipe] =
      if (connectedUserOpt.nonEmpty && user.id() == connectedUserOpt.get.id()) {
        Recipe.getDraftsByUserId(user.id())
      } else {
        Seq.empty
      }

    html.showWithRecipes(
      user,
      connectedUserOpt,
      isFollowedByConnectedUser,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      drafts,
      Make.getCountByUserId(user.id()),
      GingrsnapUser.getFollowingCount(user.id()),
      GingrsnapUser.getFollowerCount(user.id()),
      recipeFeed)
  } getOrElse {
    NotFound("No such user")
  }

  /**
   * Show a user's profile with a feed of their events.
   */
  @NonSecure def showWithEvents(userSlug: String) = GingrsnapUser.getBySlug(userSlug) map { user =>
    val eventFeed = Event.getMostRecentByUserId(user.id(), 10) map { e =>
      Event.hydrate(e)
    }
    val connectedUserOpt = Authentication.getLoggedInUser
    val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
      Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
    }).getOrElse(false)
    val drafts: Seq[Recipe] =
      if (connectedUserOpt.nonEmpty && user.id() == connectedUserOpt.get.id()) {
        Recipe.getDraftsByUserId(user.id())
      } else {
        Seq.empty
      }

    html.showWithEvents(
      user,
      connectedUserOpt,
      isFollowedByConnectedUser,
      Account.getByGingrsnapUserId(user.id()).get,
      Image.getBaseUrlByUserId(user.id()),
      drafts,
      Make.getCountByUserId(user.id()),
      GingrsnapUser.getFollowingCount(user.id()),
      GingrsnapUser.getFollowerCount(user.id()),
      eventFeed)
  } getOrElse {
    NotFound("No such user")
  }

  /**
   * Show a user's profile with a list of the users that they follow..
   */
  @NonSecure def showWithFollowing(userSlug: String) =
    GingrsnapUser.getBySlug(userSlug) map { user =>
      val connectedUserOpt = Authentication.getLoggedInUser

      val followingWithImages = GingrsnapUser.getFollowing(user.id()) map { following =>
        val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
          Some(Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), following.id()))
        }).getOrElse(None)

        (following, isFollowedByConnectedUser, Image.getBaseUrlByUserId(following.id()))
      }

      val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
        Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
      }).getOrElse(false)

      val drafts: Seq[Recipe] =
        if (connectedUserOpt.nonEmpty && user.id() == connectedUserOpt.get.id()) {
          Recipe.getDraftsByUserId(user.id())
        } else {
          Seq.empty
        }

      html.showWithFollowing(
        user,
        connectedUserOpt,
        isFollowedByConnectedUser,
        Account.getByGingrsnapUserId(user.id()).get,
        Image.getBaseUrlByUserId(user.id()),
        drafts,
        Make.getCountByUserId(user.id()),
        GingrsnapUser.getFollowingCount(user.id()),
        GingrsnapUser.getFollowerCount(user.id()),
        followingWithImages)
    } getOrElse {
      NotFound("No such user")
    }

  /**
   * Show a user's profile with a list of their followers.
   */
  @NonSecure def showWithFollowers(userSlug: String) =
    GingrsnapUser.getBySlug(userSlug) map { user =>
      val connectedUserOpt = Authentication.getLoggedInUser

      val followersWithImages = GingrsnapUser.getFollowers(user.id()) map { follower =>
        val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
          Some(Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), follower.id()))
        }).getOrElse(None)

       (follower, isFollowedByConnectedUser, Image.getBaseUrlByUserId(follower.id()))
      }

      val eventFeed = Event.getMostRecentByUserId(user.id(), 10) map { e =>
        Event.hydrate(e)
      }



      val isFollowedByConnectedUser = (connectedUserOpt map { connectedUser =>
        Follow.exists(FollowType.GingrsnapUser, connectedUser.id(), user.id())
      }).getOrElse(false)

      val drafts: Seq[Recipe] =
        if (connectedUserOpt.nonEmpty && user.id() == connectedUserOpt.get.id()) {
          Recipe.getDraftsByUserId(user.id())
        } else {
          Seq.empty
        }

      html.showWithFollowers(
        user,
        connectedUserOpt,
        isFollowedByConnectedUser,
        Account.getByGingrsnapUserId(user.id()).get,
        Image.getBaseUrlByUserId(user.id()),
        drafts,
        Make.getCountByUserId(user.id()),
        GingrsnapUser.getFollowingCount(user.id()),
        GingrsnapUser.getFollowerCount(user.id()),
        followersWithImages)
    } getOrElse {
      NotFound("No such user")
    }
}
