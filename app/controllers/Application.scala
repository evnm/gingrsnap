package controllers

import play._
import play.mvc._

object Application extends Controller with RenderCachedUser {
  import views.Application._

  /**
   * Root page.
   */
  def index = html.index()

  /**
   * About/features page.
   */
  def about = html.about()
}
