package controllers

import java.lang.IllegalStateException
import models.GingrsnapUser
import play._
import play.cache.Cache
import play.mvc._
import secure.{NonSecure, PasswordCredential, TwAuthCredential}
import twitter4j.{Twitter, TwitterFactory}
import twitter4j.auth.{AccessToken, RequestToken}

/**
 * OAuth endpoint controller. Doesn't render any views.
 */
object OAuth extends BaseController with Secure {
  import Constants._
  import views.OAuth.html

  val authCallbackUrl = play.configuration("application.baseUrl") + "oauth/twitter"
  val connectCallbackUrl = play.configuration("application.baseUrl") + "oauth/connect/twitter"

  /**
   * Authenticate a user against an external service. (i.e. Twitter)
   */
  @NonSecure def authenticate(
    externalService: String,
    oauth_token: String = "",
    oauth_verifier: String = ""
  ) = externalService match {
    case "twitter" => handleTwitter(oauth_token, oauth_verifier)
    case _ => {
      Logger.error("Invalid auth service provided in authenticate: %s".format(externalService))
      Action(Application.index)
    }
  }

  private[controllers] def mkCacheKey(key: String) = key + ":" + session.getId()

  /**
   * Handles the OAuth dance of signing in with Twitter.
   */
  protected[this] def handleTwitter(oauth_token: String, oauth_verifier: String) = try {
    val twitterIface = Cache.get(mkCacheKey(TwIfaceCacheKey)).getOrElse {
      new TwitterFactory().getInstance()
    }
    Cache.add(mkCacheKey(TwIfaceCacheKey), twitterIface, "15mn")

    val requestToken: RequestToken = Cache.get(mkCacheKey(TwRequestTokenCacheKey)).getOrElse {
      twitterIface.getOAuthRequestToken(authCallbackUrl)
    }
    Cache.add(mkCacheKey(TwRequestTokenCacheKey), requestToken, "15mn")

    if (oauth_token.isEmpty && oauth_verifier.isEmpty) {
      // Set Twitter OAuth flow in motion.
      flash.keep
      Redirect(requestToken.getAuthenticationURL())
    } else {
      val accessToken = twitterIface.getOAuthAccessToken(requestToken, oauth_verifier)
      Cache.delete(mkCacheKey(TwRequestTokenCacheKey));

      GingrsnapUser.getByTwAuth(accessToken) match {
        case Some(user) => {
          // User with oauth creds is in db, log them in.
          Authentication.authenticate(user.emailAddr, TwAuthCredential(accessToken))
          Cache.delete(mkCacheKey(TwIfaceCacheKey))

          val url = flash.get("url")
          if (url != null) {
            Redirect(url)
          } else {
            Action(Application.index)
          }
        }
        case None => {
          // Redirect to "connect twitter user with gingrsnap user page".
          val twUser = twitterIface.verifyCredentials()
          Cache.delete(mkCacheKey(TwIfaceCacheKey))
          Action(OAuth.linkTwitterPrompt(
            twUser.getScreenName(), accessToken.getToken(), accessToken.getTokenSecret()))
        }
      }
    }
  } catch {
    case e: Throwable =>
      Logger.error("Error during Twitter OAuth authentication: %s".format(e.toString))
  }

  /**
   * Connect a user's account with one on an external auth service.
   */
  @NonSecure def connect(
    externalService: String,
    oauth_token: String = "",
    oauth_verifier: String = ""
  ) = {
    externalService match {
      case "twitter" => connectTwitter(oauth_token, oauth_verifier)
      case _ => {
        Logger.error("Invalid auth service provided in authenticate: %s".format(externalService))
        Action(Application.index)
      }
    }
  }

  protected[this] def connectTwitter(oauth_token: String, oauth_verifier: String) = try {
    val twitterIface = Cache.get(mkCacheKey(TwIfaceCacheKey)).getOrElse {
      new TwitterFactory().getInstance()
    }
    Cache.add(mkCacheKey(TwIfaceCacheKey), twitterIface, "15mn")

    val requestToken: RequestToken = Cache.get(mkCacheKey(TwRequestTokenCacheKey)).getOrElse {
      twitterIface.getOAuthRequestToken(connectCallbackUrl)
    }
    Cache.add(mkCacheKey(TwRequestTokenCacheKey), requestToken, "15mn")

    if (oauth_token.isEmpty && oauth_verifier.isEmpty) {
      // Set Twitter OAuth flow in motion.
      Redirect(requestToken.getAuthenticationURL())
    } else {
      val accessToken = twitterIface.getOAuthAccessToken(requestToken, oauth_verifier)
      Cache.delete(mkCacheKey(TwRequestTokenCacheKey));
      Cache.delete(mkCacheKey(TwIfaceCacheKey))

      Authentication.getLoggedInUser match {
        case Some(user) => {
          // Connect user and redirect to Account edit page.
          GingrsnapUser.update(
            user.copy(
              twAccessToken = Some(accessToken.getToken()),
              twAccessTokenSecret = Some(accessToken.getTokenSecret())))
          flash.success("Your Twitter account has been connected")
          Action(Accounts.edit)
        }
        case None => {
          Action(GingrsnapUsers.neue(accessToken.getToken(), accessToken.getTokenSecret()))
        }
      }
    }
  } catch { case e: Throwable =>
    Logger.error("Error during Twitter OAuth connection: %s".format(e.toString))
  }

  /**
   * Handles GET to Twitter account linking route.
   */
  @NonSecure def linkTwitterPrompt(
    twUsername: String,
    twAccessToken: String,
    twAccessTokenSecret: String
  ) = try {
    val twitterIface = new TwitterFactory().getInstance()
    twitterIface.setOAuthAccessToken(new AccessToken(twAccessToken, twAccessTokenSecret))
    val twUser = twitterIface.verifyCredentials()
    val imgUrl = twUser.getProfileImageURL().toString
    html.linkTwitter(twUsername, twAccessToken, twAccessTokenSecret, imgUrl)
  } catch { case e: Throwable =>
    flash.error("Login failed")
    Action(Authentication.login)
  }

  /**
   * Handles PUT to Twitter account linking route.
   */
  @NonSecure def linkTwitter(
    twUsername: String,
    imgUrl: String,
    emailAddr: String,
    password: String,
    twAccessToken: String,
    twAccessTokenSecret: String
  ) = GingrsnapUser.getByEmailAndPass(emailAddr, password) match {
    case Some(user) => {
      GingrsnapUser.update(
        user.copy(
          twAccessToken = Some(twAccessToken),
          twAccessTokenSecret = Some(twAccessTokenSecret)))
      Authentication.authenticate(user.emailAddr, PasswordCredential(password))
      Action(Application.index)
    }
    case None => {
      flash.error("Login failed")
      html.linkTwitter(twUsername, twAccessToken, twAccessTokenSecret, imgUrl)
    }
  }

  /**
   * Revokes a user's connection to an external auth service.
   */
  def revoke(externalService: String) = {
    externalService match {
      case "twitter" => revokeTwitter()
      case _ => Logger.error(
        "Invalid external authentication service provided in revoke: %s".format(externalService))
    }
    Action(Accounts.edit)
  }

  protected[this] def revokeTwitter() = Authentication.getLoggedInUser match {
    case Some(user) => {
      val newUser = user.copy(twAccessToken = None, twAccessTokenSecret = None)
      GingrsnapUser.update(newUser)
      Action(Accounts.edit)
    }
    case None => Action(Application.index)
  }
}
