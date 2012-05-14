package controllers

import models.{Follow, FollowType, GingrsnapUser, Recipe}
import play._
import play.data.validation.Validation
import play.db.anorm.SqlRequestError
import play.mvc._
import scala.collection.JavaConversions._

object Follows extends Controller with Secure {
  def followUser(userId: Long) = Authentication.getLoggedInUser match {
    case Some(connectedUser) => {
      Validation.required("userId", userId)
      Validation.isTrue(
        "userId",
        !Follow.exists(FollowType.UserToUser, connectedUser.id(), userId)
      ).message("You already follow this user.");

      if (Validation.hasErrors) {
        // Just return the text of the first error.
        Json("{\"error\": \"" + Validation.errors()(0) + "\"}")
      } else {
        Follow.create(
          Follow(FollowType.UserToUser, connectedUser.id(), userId)
        ).toOptionLoggingError match {
          case Some(_) => Json("{\"response\": \"200\"}")
          case None => Json("{\"error\": \"Failed to create Follow record\"}")
        }
      }
    }
    case None => Json("{\"error\": \"No user logged in\"}")
  }

  def deleteUserFollow(userId: Long) = Authentication.getLoggedInUser match {
    case Some(connectedUser) => {
      Validation.required("userId", userId)
      Validation.isTrue(
        "userId",
        Follow.exists(FollowType.UserToUser, connectedUser.id(), userId)
      ).message("You don't follow this user.");

      if (Validation.hasErrors) {
        // Just return the text of the first error.
        Json("{\"error\": \"" + Validation.errors()(0) + "\"}")
      } else {
        Follow.delete(FollowType.UserToUser, connectedUser.id(), userId)
        Json("{\"response\": \"200\"}")
      }
    }
    case None => Json("{\"error\": \"No user logged in\"}")
  }
}
