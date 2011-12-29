package controllers

import java.lang.IllegalStateException
import models.User
import play._
import play.cache.Cache
import play.mvc._
import secure.{NonSecure, TwAuthCredential}
import twitter4j.{Twitter, TwitterFactory}
import twitter4j.auth.RequestToken

/**
 * OAuth endpoint controller. Doesn't render any views.
 */
object OAuth extends Controller with RenderCachedUser with Secure {
  import Constants._

  val callbackUrl = "http://localhost:9000/oauth/twitter"

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
      // TODO: Add proper error logging.
      println("Invalid external authentication service provided in authenticate: " + externalService)
      Action(Application.index)
    }
  }

  /**
   * Handles the OAuth dance of signing in with Twitter.
   */
  protected[this] def handleTwitter(oauth_token: String, oauth_verifier: String) = try {
    val twitterIface = Cache.get(TwIfaceCacheKey).getOrElse {
      new TwitterFactory().getInstance()
    }
    Cache.add(TwIfaceCacheKey, twitterIface, "15mn")

    val requestToken: RequestToken = Cache.get(TwRequestTokenCacheKey).getOrElse {
      twitterIface.getOAuthRequestToken(callbackUrl)
    }
    Cache.add(TwRequestTokenCacheKey, requestToken, "15mn")

    if (oauth_token.isEmpty && oauth_verifier.isEmpty) {
      // Set Twitter OAuth flow in motion.
      Redirect(requestToken.getAuthenticationURL())
    } else {
      val accessToken = twitterIface.getOAuthAccessToken(requestToken, oauth_verifier)
      Cache.delete(TwRequestTokenCacheKey);
      val user = User.getByTwAuth(accessToken)

      if (user.isDefined) {
        // If user with oauth creds is in db, log them in.
        user map { u =>
          Authentication.authenticate(u.emailAddr, TwAuthCredential(accessToken))
        }
        Action(Application.index)
      } else {
        Cache.get[User](Constants.UserObjKey) match {
          case Some(user: User) => {
            // User is connected, so add the Twitter connection.
            val newUser = user.copy(
              twAccessToken = Some(accessToken.getToken()),
              twAccessTokenSecret = Some(accessToken.getTokenSecret()))
            User.update(newUser)
            Cache.set(UserObjKey, newUser, "30mn")
            Cache.delete(TwIfaceCacheKey)
            Action(Accounts.edit)
          }
          case None => {
            // Snag access token pair and redirect to signup page.
            Cache.set(TwAccessTokenCacheKey, accessToken, "15mn")
            Cache.set(TwUserObjCacheKey, twitterIface.verifyCredentials(), "15mn")
            Cache.delete(TwIfaceCacheKey)
            Action(Users.neue)
          }
        }
      }
    }
  } catch {
    case e: Throwable => e.printStackTrace()
  }

  /**
   * Revokes a user's connection to an external auth service.
   */
  def revoke(externalService: String) = {
    externalService match {
      case "twitter" => revokeTwitter()
      case _ => {
        // TODO: Add proper error logging.
        println("Invalid external authentication service provided in revoke: " + externalService)
      }
    }
    Action(Accounts.edit)
  }

  protected[this] def revokeTwitter() = {
    val user = Cache.get[User](Constants.UserObjKey).get
    val newUser = user.copy(twAccessToken = None, twAccessTokenSecret = None)
    User.update(newUser)
    Cache.set(UserObjKey, newUser, "30mn")
    Action(Accounts.edit)
  }
}
