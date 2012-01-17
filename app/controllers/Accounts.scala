package controllers

import Constants.{AccountObjKey, GingrsnapUserObjKey}
import java.io.File
import models.{Account, GingrsnapUser, Image}
import play._
import play.cache.Cache
import play.data.validation.Validation
import play.db.anorm.Id
import play.libs.Crypto
import play.mvc._
import secure._

object Accounts extends BaseController with Secure {
  import views.Accounts.html

  /**
   * GET request to /account. Handles display of account editing form.
   */
  def edit() = Authentication.getLoggedInUser match {
    case Some(user) => {
      val account = Cache.get[Account](AccountObjKey).getOrElse {
        Account.getByGingrsnapUserId(user.id()).get
      }
      Cache.add(user.id() + ":" + AccountObjKey, account, "30mn")
      html.edit(
        user.fullname,
        user.emailAddr,
        play.configuration("application.baseUrl"),
        user.slug,
        user.twAccessToken.isDefined && user.twAccessTokenSecret.isDefined,
        account.location.getOrElse(""),
        account.url.getOrElse(""),
        Image.getBaseUrlByUserId(user.id()))
    }
    case None => {
      // Should never get here, but redirect just in case.
      // TODO: Add logging.
      Action(Application.index)
    }
  }

  /**
   * POST request to /account. Handles account save action.
   */
  def update(
    fullname: String,
    emailAddr: String,
    location: String,
    slug: String,
    url: String,
    oldPassword: String,
    newPassword: String,
    image: File
  ) = Authentication.getLoggedInUser match {
    case Some(user) => {
      val account = Cache.get[Account](AccountObjKey).getOrElse {
        Account.getByGingrsnapUserId(user.id()).get
      }
      Cache.add(user.id() + ":" + AccountObjKey, account, "30mn")

      if (emailAddr.nonEmpty && emailAddr != user.emailAddr) {
        Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
        Validation.isTrue(
          "emailAddr",
          GingrsnapUser.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
        ).message("Email address has already been registered")
      }

      if (slug.nonEmpty && slug != user.slug) {
        Validation.isTrue(
          "slug",
          GingrsnapUser.slugIsUnique(slug)
        ).message("Gingrsnap URL is taken by someone else. Please choose another.")
      }

      if (url.nonEmpty) {
        // TODO: Ugh. Validation.url enforces protocol.
        Validation.url("url", url).message("Must provide a valid URL of your website")
      }

      if (newPassword.nonEmpty) {
        Validation.required("oldPassword", oldPassword)
          .message("Old password is required when updating to new one")
        Validation.isTrue(
          "oldPassword",
          GingrsnapUser.validatePassword(user, oldPassword)
        ).message("Old password is incorrect")
      }

      if (!Validation.hasErrors) {
        val newGingrsnapUser = user.copy(
          emailAddr = if (emailAddr.isEmpty) user.emailAddr else emailAddr,
          slug = if (slug.isEmpty) user.slug else slug,
          password =
            if (newPassword.isEmpty)
              user.password
            else
              Crypto.passwordHash(user.salt + newPassword),
          fullname = if (fullname.isEmpty) user.fullname else fullname)
        GingrsnapUser.update(newGingrsnapUser)
        val userId = newGingrsnapUser.id()

        val newLocation = if (location.isEmpty) None else Some(location)
        val newUrl = if (url.isEmpty) None else Some(url)
        val newAccount = account.copy(
          location = newLocation,
          url = newUrl)
        Account.update(newAccount, if (image == null) None else Some(image))
        Cache.set(userId + ":" + AccountObjKey, newAccount, "30mn")
        flash.success("Saved.")
      }

      Accounts.edit()
    }
    case None => {
      // Should never get here, but redirect just in case.
      // TODO: Add logging.
      Action(Application.index)
    }
  }
}
