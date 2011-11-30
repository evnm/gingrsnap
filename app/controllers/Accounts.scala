package controllers

import models.User
import play._
import play.mvc._
import secure._

object Accounts extends Controller with Secure with RenderCachedUser {
  import views.Accounts.html

  /**
   * GET Request to /account. Handles display of account editing form.
   */
  def edit() = {
    html.edit()
  }

  /*
   * POST Request to /account. Handles account save action.
   */
  def update() = {}
}
