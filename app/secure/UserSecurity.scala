package secure

import controllers.Constants.UserObjKey
import models.User
import play.cache.Cache
import twitter4j.auth.AccessToken

class UserSecurity extends Security[User] {
  override def authenticate[U <: Credential](
    emailAddr: String, credential: U
  ) = {
    val optUser = credential match {
      case PasswordCredential(password) => User.getByEmailAndPass(emailAddr, password)
      case TwAuthCredential(token) => User.getByTwAuth(token)
    }
    optUser match {
      case Some(user: User) => user
      case None => throw new AuthenticationFailureException
    }
  }

  override def authorize(token: User, role: String) = false

  override def onSuccessfulLogin(emailAddr: String) = {
    // TODO: Verify that this .get can't blow up.
    Cache.add(UserObjKey, User.getByEmail(emailAddr).get, "30mn")
  }

  override def onSuccessfulLogout(emailAddr: String) = {
    Cache.delete(UserObjKey)
  }
}
