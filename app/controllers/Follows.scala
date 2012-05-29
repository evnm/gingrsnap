package controllers

import models.{Follow, FollowType, GingrsnapUser, Recipe, RecipeList}
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
        Json("{\"error\": \"" + Validation.errors.get(0) + "\"}")
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
        Json("{\"error\": \"" + Validation.errors.get(0) + "\"}")
      } else {
        if (Follow.delete(FollowType.UserToUser, connectedUser.id(), userId))
          Json("{\"response\": \"200\"}")
        else
          Json("{\"error\": \"Failed to remove Follow record\"}")
      }
    }
    case None => Json("{\"error\": \"No user logged in\"}")
  }

  def newRecipeListFollow(listId: Long, recipeId: Long) = Authentication.getLoggedInUser match {
    case Some(connectedUser) => {
      Validation.required("listId", listId)
      Validation.required("recipeId", listId)
      Validation.isTrue(
        "listId+recipeId",
        !Follow.exists(FollowType.ListToRecipe, listId, recipeId)
      ).message("This recipe is already on that list")
      Validation.isTrue(
        "listId+userId",
        RecipeList.getByUserIdAndListId(connectedUser.id(), listId).isDefined
      ).message("You can't add recipes to lists that aren't yours")

      if (Validation.hasErrors) {
        // Just return the text of the first error.
        flash.error(Validation.errors.get(0).message)
      } else {
        RecipeList.getById(listId) flatMap { list =>
          Recipe.getById(recipeId) map { recipe =>
            Follow.create(
              Follow(FollowType.ListToRecipe, listId, recipeId)
            ).toOptionLoggingError match {
              case Some(_) =>
                flash.success("Added %s from your list, %s".format(recipe.title, list.title))
              case None =>
                flash.error("Failed to add %s to your list, %s".format(recipe.title, list.title))
            }
          }
        } getOrElse {
          flash.error("Failed to add recipe to list")
        }
      }

      var url = flash.get("url")
      flash.discard("url")
      Redirect(url)
    }

    case None => Action(Application.index)

      /*
      if (Validation.hasErrors) {
        // Just return the text of the first error.
        Json("{\"error\": \"" + Validation.errors.get(0) + "\"}")
      } else {
        Follow.create(
          Follow(FollowType.ListToRecipe, listId, recipeId)
        ).toOptionLoggingError match {
          case Some(_) => Json("{\"response\": \"200\"}")
          case None => Json("{\"error\": \"Failed to create Follow record\"}")
        }
      }
    }
    case None => Json("{\"error\": \"No user logged in\"}")
    */
  }

  def deleteRecipeListFollow(listId: Long, recipeId: Long) = Authentication.getLoggedInUser match {
    case Some(connectedUser) => {
      Validation.required("listId", listId)
      Validation.required("recipeId", listId)
      Validation.isTrue(
        "listId+recipeId",
        Follow.exists(FollowType.ListToRecipe, listId, recipeId)
      ).message("This recipe is not on that list")
      Validation.isTrue(
        "listId+userId",
        RecipeList.getByUserIdAndListId(connectedUser.id(), listId).isDefined
      ).message("You can't remove recipes from lists that aren't yours")

      if (Validation.hasErrors) {
        // Just return the text of the first error.
        flash.error(Validation.errors.get(0).message)
      } else {
        RecipeList.getById(listId) flatMap { list =>
          Recipe.getById(recipeId) map { recipe =>
            if (Follow.delete(FollowType.ListToRecipe, listId, recipeId))
              flash.error("Failed to remove %s from your list, %s".format(recipe.title, list.title))
            else
              flash.success("Removed %s from your list, %s".format(recipe.title, list.title))
          }
        } getOrElse {
          flash.error("Failed to remove Follow record")
        }
      }

      var url = flash.get("url")
      flash.discard("url")
      Redirect(url)
    }

    case None => Action(Application.index)
  }
}
