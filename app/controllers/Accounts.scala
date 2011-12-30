package controllers

import Constants.{AccountObjKey, GingrsnapUserObjKey}
import models.{Account, GingrsnapUser}
import play._
import play.cache.Cache
import play.data.validation.Validation
import play.db.anorm.Id
import play.libs.Crypto
import play.mvc._
import secure._

object Accounts extends Controller with RenderCachedGingrsnapUser with Secure {
  import views.Accounts.html

  /**
   * GET request to /account. Handles display of account editing form.
   */
  def edit() = {
    val user = Cache.get[GingrsnapUser](GingrsnapUserObjKey).get
    val account = Cache.get[Account](AccountObjKey).getOrElse {
      Account.getByGingrsnapUserId(user.id()).get
    }
    Cache.add(AccountObjKey, account, "30mn")
    html.edit(
      user.fullname,
      user.emailAddr,
      user.twAccessToken.isDefined && user.twAccessTokenSecret.isDefined,
      account.location.getOrElse(""),
      account.url.getOrElse(""))
  }

  /*
   * POST request to /account. Handles account save action.
   */
  def update(
    fullname: String,
    emailAddr: String,
    location: String,
    url: String,
    oldPassword: String,
    newPassword: String
  ) = {
    val user = Cache.get[GingrsnapUser](GingrsnapUserObjKey).get
    val account = Cache.get[Account](AccountObjKey).getOrElse {
      Account.getByGingrsnapUserId(user.id()).get
    }
    Cache.add(AccountObjKey, account, "30mn")

    if (emailAddr.nonEmpty && emailAddr != user.emailAddr) {
      Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
      Validation.isTrue(
        "emailAddr",
        GingrsnapUser.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
      ).message("Email address has already been registered")
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
        password =
          if (newPassword.isEmpty)
            user.password
          else
            Crypto.passwordHash(user.salt + newPassword),
        fullname = if (fullname.isEmpty) user.fullname else fullname)
      GingrsnapUser.update(newGingrsnapUser)
      Cache.set(GingrsnapUserObjKey, newGingrsnapUser, "30mn")

      val newLocation = if (location.isEmpty) None else Some(location)
      val newUrl = if (url.isEmpty) None else Some(url)
      val newAccount = Account(Id(account.id()), account.userId, newLocation, newUrl)
      Account.update(newAccount)
      Cache.set(AccountObjKey, newAccount, "30mn")
      flash.success("Saved.")
    }

    Accounts.edit()
  }
}
