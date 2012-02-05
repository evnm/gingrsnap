package controllers

import java.io.File
import models.{Account, GingrsnapUser, Image, PasswordResetRequest}
import play._
import play.data.validation.Validation
import play.db.anorm.{Id, NotAssigned}
import play.libs.Crypto
import play.mvc._
import secure.{NonSecure, PasswordCredential}

object Accounts extends BaseController with Secure {
  import views.Accounts.html

  /**
   * Validates fields from Account inputs.
   */
  protected[this] def validateAccount(
    user: GingrsnapUser,
    fullname: String,
    emailAddr: String,
    slug: String,
    url: String,
    oldPassword: String,
    newPassword: String
  ) = {
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
  }

  /**
   * GET request to /account. Handles display of account editing form.
   */
  def edit() = Authentication.getLoggedInUser match {
    case Some(user) => Account.getByGingrsnapUserId(user.id()) match {
      case Some(account) => {
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
        Logger.error("Accounts.edit: User with id %s has no associated account".format(user.id()))
        Action(Application.index)
      }
    }
    case None => {
      Logger.error("Accounts.edit: No user logged in")
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
    case Some(user) => Account.getByGingrsnapUserId(user.id()) match {
      case Some(account) => {
        validateAccount(user, fullname, emailAddr, slug, url, oldPassword, newPassword)

        if (!Validation.hasErrors) {
          GingrsnapUser.update(
            user.copy(
              emailAddr = if (emailAddr.isEmpty) user.emailAddr else emailAddr,
              slug = if (slug.isEmpty) user.slug else slug,
              password = {
                if (newPassword.isEmpty)
                  user.password
                else
                  Crypto.passwordHash(user.salt + newPassword)
              },
              fullname = if (fullname.isEmpty) user.fullname else fullname))

          val newLocation = if (location.isEmpty) None else Some(location)
          val newUrl = if (url.isEmpty) None else Some(url)
          val newAccount = account.copy(
            location = newLocation,
            url = newUrl)

          Account.update(
            newAccount,
            if (image == null) None else Some(image))

          flash.success("Saved")
        }

        Action(Accounts.edit)
      }
      case None => {
        Logger.error("Accounts.update: User with id %s has no associated account".format(user.id()))
        Action(Application.index)
      }
    }
    case None => {
      Logger.error("Accounts.update: No user logged in")
      Action(Application.index)
    }
  }

  /**
   * Displays password reset prompt page.
   */
  @NonSecure def passwordResetPrompt() = {
    html.passwordResetRequestForm()
  }

  /**
   * Handles password-reset POST request.
   */
  @NonSecure def sendPasswordResetRequest(emailAddr: String) = GingrsnapUser.getByEmail(emailAddr) match {
    case Some(user) => {
      // One reset per 24-hour period.
      val prevReset = PasswordResetRequest.getMostRecentByUserId(user.id())
      if (prevReset.isEmpty || !PasswordResetRequest.isCompletable(prevReset.get)) {
        (Account.passwordReset(user) map { _: PasswordResetRequest =>
          html.passwordResetEmailSent(user.emailAddr)
        }).toOptionLoggingError.getOrElse {
          flash.error("A problem arose during the password reset process")
          Action(Application.index)
        }
      } else {
        flash.error("You can only send one password reset request every 24 hours")
        Action(Application.index)
      }
    }
    case None => {
      flash.error("There's no user registered to that email address")
      html.passwordResetRequestForm()
    }
  }

  /**
   * Displays password-reset form.
   */
  @NonSecure def passwordResetForm(uuid: String) = PasswordResetRequest.getById(uuid) match {
    case Some(pwdResetRequest) if !pwdResetRequest.resetCompleted =>  {
      if (PasswordResetRequest.isCompletable(pwdResetRequest)) {
        GingrsnapUser.getById(pwdResetRequest.userId) map { user =>
          html.passwordResetForm(user.id(), user.fullname, pwdResetRequest.id())
        } getOrElse(Action(Application.index))
      } else {
        flash.error("Your most recent password reset request has expired. Please try again.")
        Action(Application.index)
      }
    }
    case _ => {
      flash.error("Invalid password reset request")
      Action(Application.index)
    }
  }

  /**
   * Reset a user's password to a given value.
   */
  @NonSecure def resetPassword(
    userId: Long,
    pwdResetRequestId: String,
    newPassword: String,
    newPasswordConfirm: String
  ) = PasswordResetRequest.getById(pwdResetRequestId) match {
    case Some(pwdResetRequest) => {
      GingrsnapUser.getById(pwdResetRequest.userId) map { user =>
        Validation.isTrue("newPassword", newPassword == newPasswordConfirm)
          .message("Password and confirmation must be equal")
        Validation.isTrue("newPassword", newPassword.size >= 6)
          .message("Password must be at least 6 characters long")

        if (!Validation.hasErrors) {
          GingrsnapUser.updatePassword(user, newPassword)
          Authentication.authenticate(user.emailAddr, PasswordCredential(newPassword))
          PasswordResetRequest.update(pwdResetRequest.copy(resetCompleted = true))
          flash.success("Password successfully updated")
          Action(Accounts.edit)
        } else {
          html.passwordResetForm(user.id(), user.fullname, pwdResetRequestId)
        }
      } getOrElse {
        flash.error("Invalid password reset request")
        Action(Application.index)
      }
    }
    case None => {
      flash.error("Invalid password reset request")
      Action(Application.index)
    }
  }
}
