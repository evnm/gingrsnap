package controllers

import models.User
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends Controller with RenderCachedUser {
  /**
   * Root page.
   */
  def index = Cache.get[User](Constants.UserObjKey) match {
    case Some(user: User) => views.Users.html.home(user)
    case None => views.Application.html.index()
  }

  /**
   * About/features page.
   */
  def about = views.Application.html.about()
}
