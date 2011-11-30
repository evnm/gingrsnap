package secure

import controllers.Constants.UserObjKey
import models.User
import play.cache.Cache

class UserSecurity extends Security[User] {
  override def authenticate(
    emailAddr: String, password: String
  ) = User.getByEmailAndPass(emailAddr, password) match {
    case Some(user: User) => user
    case None => throw new AuthenticationFailureException
  }

  override def authorize(token: User, role: String) = false

  override def onSuccessfulLogin(emailAddr: String) = {
    Cache.add(UserObjKey, User.getByEmail(emailAddr), "30mn")
  }

  override def onSuccessfulLogout(emailAddr: String) = {
    Cache.delete(UserObjKey)
  }
}
