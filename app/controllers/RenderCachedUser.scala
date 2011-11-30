package controllers

import Constants.UserObjKey
import models.User
import play.cache.Cache
import play.mvc._

trait RenderCachedUser { self: Controller =>
  /**
   * Gets the currently-logged-in User from Cache or else fetches and
   * caches User from db.
   *
   * TODO: Unit test this, somehow.
   */
  private[this] def getCachedUser(): Option[User] = {
    Cache.get[User](UserObjKey) match {
      case userOpt: Some[User] => userOpt
      case None =>
        if (self.session.get("username") == null) {
          None
        } else {
          User.getByEmail(self.session.get("username")) map { user =>
            Cache.set(UserObjKey, user, "30mn")
            user
          }
        }
    }
  }

  /**
   * Adds an Option[User] for the logged-in user to RenderArgs.
   */
  @Before def preloadLoggedInUser() = renderArgs.put(UserObjKey, getCachedUser())
}
