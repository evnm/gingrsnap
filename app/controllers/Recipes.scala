package controllers

import collection.JavaConversions._
import Constants.UserObjKey
import models.{Ingredient, Recipe, User}
import play._
import play.cache.Cache
import play.data.validation.Validation
import play.db.anorm.SqlRequestError
import play.mvc._
import secure.NonSecure

object Recipes extends Controller with RenderCachedUser with Secure {
  import views.Recipes.html

  /*
   * Recipe creation page.
   */
  def neue() = {
    val user = Cache.get[User](UserObjKey).get
    html.neue(user.id())
  }

  /*
   * Recipe creation POST handler.
   */
  def create(
    title: String,
    slug: String,
    authorId: Long,
    ingredients: java.util.List[String], // comma-separated list as string
    recipeBody: String
  ) = {
    def handleError(error: SqlRequestError) = {
      // TODO: Proper logging.
      println("Error during creation of recipe, title(%s),slug(%s),authorId(%s)," +
              "ingredients(%s),recipeBody(%s): %s"
                .format(title, slug, authorId, ingredients.toString, recipeBody))
      flash.error("Unfortunately, there was an error while creating your account. " +
                  "Please try again.")
    }

    Validation.required("title", title).message("Title is required")
    Validation.required("slug", slug).message("You must provide a URL for your recipe")
    // TODO: Validate slug format.
    Validation.isTrue("slug", true).message("URL can only contain letters, numbers, and hyphens.")
    Validation.required("recipeBody", recipeBody).message("Your recipe must contain body text")

    if (Validation.hasErrors) {
      neue()
    } else {
      Recipe.create(Recipe(title, slug, authorId, recipeBody), ingredients)
        .toOptionLoggingError map { recipe =>
          Action(Recipes.show(recipe.authorId, recipe.slug))
        }
    }
  }

  /**
   * Look up a recipe by userId and recipe slug.
   */
  @NonSecure def show(userId: Long, slug: String) = {
    Recipe.getByAuthorIdAndSlug(userId, slug) map { case (recipe, ingredients) =>
      html.show(recipe, ingredients)
    } getOrElse {
      NotFound("No such recipe")
    }
  }

  /**
   * TODO
  @NonSecure def show(userId: Long, recipeId: Long) =
    Recipe.getByAuthorIdAndRecipeId(userId, recipeId) map { case (recipe, ingredients) =>
      html.show(recipe, ingredients)
    } getOrElse {
      NotFound("No such recipe")
    }
  */
}
