package controllers

import models.{Event, Recipe, GingrsnapUser}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends BaseController {
  /**
   * Annotating return type and nvoking GingrsnapUsers.home directly because
   * Application.index and GingrsnapUsers.home are mutually recursive.
   */
  def index: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) => GingrsnapUsers.home
    case None => {
      views.Application.html.index(
        Event.getMostRecent(20) map { Event.hydrate(_) }
      )
    }
  }

  def about = {
    views.Application.html.about(Authentication.getLoggedInUser.isDefined)
  }
}
