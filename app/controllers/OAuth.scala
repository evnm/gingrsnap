package controllers

import java.lang.IllegalStateException
import models.GingrsnapUser
import play._
import play.cache.Cache
import play.mvc._
import secure.{NonSecure, TwAuthCredential}
import twitter4j.{Twitter, TwitterFactory}
import twitter4j.auth.RequestToken

/**
 * OAuth endpoint controller. Doesn't render any views.
 */
object OAuth extends BaseController with Secure {
  import Constants._

  val callbackUrl = "http://localhost:5000/oauth/twitter"

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
    // TODO: Rework this based on session.getId().
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
      val user = GingrsnapUser.getByTwAuth(accessToken)

      if (user.isDefined) {
        // If user with oauth creds is in db, log them in.
        user map { u =>
          Authentication.authenticate(u.emailAddr, TwAuthCredential(accessToken))
        }
        Action(Application.index)
      } else {
        val emailAddr = session.get("username")
        if (emailAddr != null) {
          // User is connected, so add the Twitter connection.
          val user = GingrsnapUser.getByEmail(emailAddr).get
          GingrsnapUser.update(
            user.copy(
              twAccessToken = Some(accessToken.getToken()),
              twAccessTokenSecret = Some(accessToken.getTokenSecret())
            )
          )
          Cache.delete(TwAccessTokenCacheKey)
          Cache.delete(TwIfaceCacheKey)
          Action(Accounts.edit)
        } else {
          // Snag access token pair and redirect to signup page.
          Cache.set(TwAccessTokenCacheKey, accessToken, "15mn")
          Cache.set(TwUserObjCacheKey, twitterIface.verifyCredentials(), "15mn")
          Cache.delete(TwIfaceCacheKey)
          Action(GingrsnapUsers.neue)
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
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    val newUser = user.copy(twAccessToken = None, twAccessTokenSecret = None)
    GingrsnapUser.update(newUser)
    Action(Accounts.edit)
  }
}
