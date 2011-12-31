package controllers

import models.{Recipe, GingrsnapUser}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends BaseController {
  def index = {
    val mostRecentRecipes = Recipe.getMostRecentWithAuthors(10)
    GingrsnapUser.getByEmail(session.get("username")) match {
      case Some(user: GingrsnapUser) => views.GingrsnapUsers.html.home(
        user,
        usersRecipes = Recipe.getByGingrsnapUserId(user.id()),
        recipeFeed = mostRecentRecipes)
      case None => views.Application.html.index(mostRecentRecipes)
    }
  }

  def about = views.Application.html.about()

  def contact = views.Application.html.contact()
}
