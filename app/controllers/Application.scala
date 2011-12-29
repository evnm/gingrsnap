package controllers

import models.{Recipe, User}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends Controller with RenderCachedUser {
  def index = {
    val mostRecentRecipes = Recipe.getMostRecentWithAuthors(10)
    Cache.get[User](Constants.UserObjKey) match {
      case Some(user: User) => views.Users.html.home(
        user,
        usersRecipes = Recipe.getByUserId(user.id()),
        recipeFeed = mostRecentRecipes)
      case None => views.Application.html.index(mostRecentRecipes)
    }
  }

  def about = views.Application.html.about()

  def contact = views.Application.html.contact()
}
