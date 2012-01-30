package controllers

import models.{Feature, Recipe, Tip}
import play._
import play.data.validation.Validation
import play.mvc._

object Tips extends BaseController with Secure {
  def create(
    userId: Long,
    recipeId: Long,
    tipBody: String
  ) = if (Feature(Constants.RecipeTips)) {
    Authentication.getLoggedInUser match {
      case Some(user) => {
        Recipe.getHydratedById(recipeId) match {
          case Some((recipe, author)) => {
            Validation.required("recipeId", recipeId)
            Validation.isTrue(
              "isAllowed",
              Tip.isAllowed(userId, recipeId)
            ).message("You left a tip on this recipe too recently to have left another one!");

            if (Validation.hasErrors) {
              // Just return the text of the first error.
              Validation.keep()
              Action(Recipes.show(author.slug, recipe.slug))
            } else {
              Tip.create(Tip(userId, recipeId, tipBody)).toOptionLoggingError match {
                case Some(newTip) => {
                  flash.success("You've left a tip on this recipe")
                }
                case None => {
                  Logger.error(
                    "Error while creating tip: userId(%s), recipeId(%s), tipBody(%s)".format(
                      userId, recipeId, tipBody))
                  flash.error("There was a problem while creating your tip")
                }
              }
              Action(Recipes.show(author.slug, recipe.slug))
            }
          }
          case None => {
            Logger.error(
              "Invalid tip creation: No such recipe. userId(%s), recipeId(%s), tipBody(%s)".format(
                userId, recipeId, tipBody))
            Action(Application.index)
          }
        }
      }
      case None => {
        Logger.error(
          "Invalid tip creation: No user logged in. userId(%s), recipeId(%s), tipBody(%s)".format(
            userId, recipeId, tipBody))
        Action(Application.index)
      }
    }
  } else {
    Logger.error(
      "Request received to leave recipe tip when feature is turned off: userId(%s), recipeId(%s), tipBody(%s)".format(
        userId, recipeId, tipBody))
    NotFound
  }
}
