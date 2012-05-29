/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Created on: 16th August 2011
 */
package controllers

import play._
import play.mvc._
import play.mvc.results.Redirect
import play.libs._

import models.GingrsnapUser
import secure._

/**
 * Controller for managing authentication.
 *
 * @author Aishwarya Singhal
 */
object Authentication extends Controller {

  /**
   * Optionally returns the currently-logged-in GingrsnapUser.
   */
  def getLoggedInUser: Option[GingrsnapUser] = {
    session.get("username") match {
      case encrypted: String => {
        try {
          GingrsnapUser.getByEncryptedEmail(encrypted) orElse
            GingrsnapUser.getByEncryptedTwToken(encrypted)
        } catch {
          case e => None
        }
      }
      case _ => None
    }
  }

  /**
   * Displays the login page. If a past request for "remember me" has
   * generated a cookie, the user will be automatically logged in. Note that
   * this implementation only puts username into the session which acts as
   * a successful login token to the system.
   */
  def login = {
    val remember = request.cookies.get("rememberme");
    if (remember != null && remember.value.indexOf("-") > 0) {
      val sign = remember.value.substring(0, remember.value.indexOf("-"));
      val username = remember.value.substring(remember.value.indexOf("-") + 1);
      if (Crypto.sign(username).equals(sign)) {
        flash.put("remember", true);
        flash.put("username", username);
        session.put("username", Crypto.encryptAES(username));
        flash.keep("url");
        redirectToOriginalURL()
      }
    }
    flash.keep("url");
    views.Login.html.login()
  }


  /**
   * Authenticates user inputs in params hash against the security realm.
   */
  def authenticate: Redirect =
    authenticate(params.get("username"), PasswordCredential(params.get("password")))

  def authenticate(username: String, pwdCredential: PasswordCredential): Redirect = {
    authenticate(username.toLowerCase, pwdCredential.asInstanceOf[Credential])
  }

  /**
   * Authenticates passed user inputs against the security realm.
   * @see secure.Security
   */
  def authenticate[U <: Credential](username: String, credential: U) = {
    var url = flash.get("url")
    flash.discard("url")

    try {
      Security.authenticate(username, credential)

      // process the remember me request
      rememberMe(params.get("remember"), username)

      session.put("username", Crypto.encryptAES(username));
      flash.keep
      redirectToOriginalURL()
    } catch {
      case e: AuthenticationFailureException =>
        flash.error("Login failed", "username")
        url = "/login"
        flash.keep
        Redirect(url)
      case e: Throwable =>
        flash.error("Unknown error", "username")
        url = "/login"
        flash.keep
        Redirect(url)
    }
  }

  /**
   * Creates a secure cookie that retains the provided username if
   * "remember me" checkbox has been checked.
   */
  private def rememberMe(remember: String, username: String) = {
    // Remember if needed
    if (remember != null && remember.toBoolean) {
      response.setCookie("rememberme", Crypto.sign(username) + "-" + username, "30d");
    }
  }

  /**
   * Logs out the user and redirects to the root URL ("/").
   */
  def logout = {
    val username = session.get("username").toLowerCase
    session.clear
    flash.remove("username")
    response.removeCookie("rememberme")
    Security onSuccessfulLogout(username)
    Redirect("/")
  }

  /**
   * Shown in case a user is not not authorized to access a requested URL/ action.
   */
  def unauthorized = {
    views.Login.html.unauthorized("Unauthorized Access")
  }

  /**
   * Redirects user to the requested URL post login.
   */
  private def redirectToOriginalURL() = {
    val username = session.get("username").toLowerCase
    Security.onSuccessfulLogin(username)
    var url = flash.get("url");
    if (url == null) {
      url = "/";
    }
    Redirect(url);
  }
}
