package controllers

import models.{Make, Recipe}
import play._
import play.data.validation.Validation
import play.db.anorm.SqlRequestError
import play.mvc._
import scala.collection.JavaConversions._

object Makes extends Controller with Secure {
  def create(userId: Long, recipeId: Long) = {
    Validation.required("userId", userId)
    Validation.required("recipeId", recipeId)
    Validation.isTrue(
      "isMakable",
      Recipe.isMakable(userId, recipeId)
    ).message("You made this recipe too recently to have made it again!");

    if (Validation.hasErrors) {
      // Just return the text of the first error.
      Json("{\"error\": \"" + Validation.errors()(0) + "\"}")
    } else {
      Make.create(Make(userId, recipeId)).toOptionLoggingError match {
        case Some(newMake) => Json(
          "{\"make\": {\"id\": \"" + newMake.id() + "\", \"userId\": \"" + newMake.userId +
          "\", \"recipeId\": \"" + newMake.recipeId + "\", \"createdAt\": \"" +
          newMake.createdAt.getTime + "\"}}")
        case None => Json("{\"error\": \"Failed to create Make record\"}")
      }
    }
  }
}
