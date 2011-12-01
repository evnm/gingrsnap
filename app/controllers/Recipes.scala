package controllers

import models.Recipe
import play._
import play.mvc._

object Recipes extends Controller with RenderCachedUser {
  import views.Recipes.html

  /*
   * Recipe creation page.
   */
  def neue() = {
    html.neue()
  }

  /*
   * Recipe creation POST handler.
   */
  def create() = {}

  /**
   * TODO
   */
  def show(userId: Long, recipeId: Long) = {
    // TODO: Add full recipe page.
  }
}
