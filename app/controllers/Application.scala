package controllers

import models.User
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends Controller with RenderCachedUser {
  /**
   * Root page.
   */
  def index = {
    val mostRecentRecipes = models.Recipe.getMostRecent(10)
    Cache.get[User](Constants.UserObjKey) match {
      case Some(user: User) => views.Application.html.index(
        "Your dashboard",
        Some(user),
        mostRecentRecipes)
      case None => views.Application.html.index(
        "",
        None,
        mostRecentRecipes
      )
    }
  }

  /**
   * About/features page.
   */
  def about = views.Application.html.about()
}
