package controllers

import Constants.GingrsnapUserObjKey
import models.GingrsnapUser
import play.cache.Cache
import play.mvc.{Before, Controller}

trait RenderCachedGingrsnapUser { self: Controller =>
  /**
   * Gets the currently-logged-in GingrsnapUser from Cache or else fetches and
   * caches GingrsnapUser from db.
   *
   * TODO: Unit test this, somehow.
   */
  private[this] def getCachedGingrsnapUser() = Cache.get[GingrsnapUser](GingrsnapUserObjKey) match {
    case userOpt: Some[GingrsnapUser] => userOpt
    case None =>
      if (self.session.get("username") == null) {
        None
      } else {
        GingrsnapUser.getByEmail(self.session.get("username")) map { user =>
          Cache.set(GingrsnapUserObjKey, user, "30mn")
          user
        }
      }
  }

  /**
   * Adds an Option[GingrsnapUser] for the logged-in user to RenderArgs.
   */
  @Before def preloadLoggedInGingrsnapUser() = renderArgs.put(GingrsnapUserObjKey, getCachedGingrsnapUser())
}
