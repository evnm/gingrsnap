package controllers

import models.{Recipe, User}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends Controller with RenderCachedUser {
  /**
   * Root page.
   */
  def index = {
    val mostRecentRecipes = Recipe.getMostRecent(10)
    Cache.get[User](Constants.UserObjKey) match {
      case Some(user: User) => views.Users.html.home(
        user,
        usersRecipes = Recipe.getByUserId(user.id()),
        recipeFeed = mostRecentRecipes)
      case None => views.Application.html.index(mostRecentRecipes)
    }
  }

  /**
   * About/features page.
   */
  def about = views.Application.html.about()
}
