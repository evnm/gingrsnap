package secure

import controllers.Authentication
import controllers.Constants.GingrsnapUserObjKey
import models.GingrsnapUser
import play.cache.Cache
import twitter4j.auth.AccessToken

class GingrsnapUserSecurity extends Security[GingrsnapUser] {
  override def authenticate[U <: Credential](
    emailAddr: String, credential: U
  ) = {
    val optGingrsnapUser = credential match {
      case PasswordCredential(password) => GingrsnapUser.getByEmailAndPass(emailAddr, password)
      case TwAuthCredential(token) => GingrsnapUser.getByTwAuth(token)
    }
    optGingrsnapUser match {
      case Some(user: GingrsnapUser) => user
      case None => throw new AuthenticationFailureException
    }
  }

  override def authorize(token: GingrsnapUser, role: String) = false

  override def onSuccessfulLogin(emailAddr: String) = Authentication.getLoggedInUser match {
    case Some(user) => Cache.add(GingrsnapUserObjKey, user, "30mn")
    case None => // TODO: Add logging.
  }

  override def onSuccessfulLogout(emailAddr: String) = {
    Cache.delete(GingrsnapUserObjKey)
  }
}
