package controllers

import Constants.GingrsnapUserObjKey
import models.GingrsnapUser
import play.mvc.{Before, Controller}

trait BaseController extends Controller {
  /**
   * Adds an Option[GingrsnapUser] for the logged-in user to RenderArgs.
   */
  @Before def preloadLoggedInGingrsnapUser() = renderArgs.put(
    GingrsnapUserObjKey,
    GingrsnapUser.getByEmail(session.get("username"))
  )
}
