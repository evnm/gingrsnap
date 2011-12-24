package controllers

import Constants.{AccountObjKey, UserObjKey}
import models.{Account, User}
import play._
import play.cache.Cache
import play.data.validation.Validation
import play.db.anorm.Id
import play.libs.Crypto
import play.mvc._
import secure._

object Accounts extends Controller with RenderCachedUser with Secure {
  import views.Accounts.html

  /**
   * GET request to /account. Handles display of account editing form.
   */
  def edit() = {
    val user = Cache.get[User](UserObjKey).get
    val account = Cache.get[Account](AccountObjKey).getOrElse {
      Account.getByUserId(user.id()).get
    }
    Cache.add(AccountObjKey, account, "30mn")
    html.edit(account.location.getOrElse(""), account.url.getOrElse(""))
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
    // TODO: Should be able to set location field to empty string.
    val user = Cache.get[User](UserObjKey).get
    val account = Cache.get[Account](AccountObjKey).getOrElse {
      Account.getByUserId(user.id()).get
    }
    Cache.add(AccountObjKey, account, "30mn")

    if (emailAddr.nonEmpty && emailAddr != user.emailAddr) {
      Validation.email("emailAddr", emailAddr).message("Must provide a valid email address")
      Validation.isTrue(
        "emailAddr",
        User.count("emailAddr = {emailAddr}").on("emailAddr" -> emailAddr).single() == 0
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
        User.validatePassword(user, oldPassword)
      ).message("Old password is incorrect")
    }

    if (!Validation.hasErrors) {
      val newUser = user.copy(
        emailAddr = if (emailAddr.isEmpty) user.emailAddr else emailAddr,
        password =
          if (newPassword.isEmpty)
            user.password
          else
            Crypto.passwordHash(user.salt + newPassword),
        fullname = if (fullname.isEmpty) user.fullname else fullname)
      User.update(newUser)
      Cache.set(UserObjKey, newUser, "30mn")

      val newLocation = if (location.isEmpty) None else Some(location)
      val newUrl = if (url.isEmpty) None else Some(url)
      val newAccount = Account(Id(account.id()), account.userId, newLocation, newUrl)
      Account.update(newAccount)
      Cache.set(AccountObjKey, newAccount, "30mn")
    }

    edit()
  }
}
