package controllers

import Constants.GingrsnapUserObjKey
import controllers.Authentication
import play.mvc.{Before, Controller}

trait BaseController extends Controller {
  /**
   * Adds an Option[GingrsnapUser] for the logged-in user to RenderArgs.
   */
  @Before def preloadLoggedInGingrsnapUser() = {
    renderArgs.put(GingrsnapUserObjKey, Authentication.getLoggedInUser)
  }
}
