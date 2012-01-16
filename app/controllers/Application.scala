package controllers

import models.{Event, Recipe, GingrsnapUser}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends BaseController {
  def index = {
    val mostRecentEvents = Event.getMostRecent(10) map { e =>
      Event.hydrate(e)
    }
    Authentication.getLoggedInUser match {
      case Some(user) => views.GingrsnapUsers.html.home(
        user,
        usersRecipes = Recipe.getByUserId(user.id()),
        eventFeed = mostRecentEvents)
      case None => views.Application.html.index(mostRecentEvents)
    }
  }

  def about = views.Application.html.about()

  def contact = views.Application.html.contact()
}
