package controllers

import collection.JavaConversions._
import Constants.GingrsnapUserObjKey
import java.io.File
import java.sql.Timestamp
import models.{Image, Ingredient, Make, Recipe, RecipeImage, GingrsnapUser}
import play._
import play.data.validation.Validation
import play.db.anorm.SqlRequestError
import play.mvc._
import scala.collection.JavaConversions._
import secure.NonSecure

object Recipes extends BaseController with Secure {
  import views.Recipes.html

  /**
   * Validates fields from recipe inputs.
   */
  protected[this] def validateRecipe(
    title: String,
    slug: String,
    ingredients: java.util.List[String] = new java.util.ArrayList[String],
    recipeBody: String
  ) = {
    Validation.required("title", title)
      .message("Title is required")
    Validation.required("slug", slug)
      .message("You must provide a URL for your recipe")
    // TODO: Validate slug format.
    Validation.isTrue("slug", true)
      .message("URL can only contain letters, numbers, and hyphens.")
    Validation.isTrue("ingredients", !ingredients.isEmpty)
      .message("You must provide at least one ingredient")
    Validation.required("recipeBody", recipeBody)
      .message("Your recipe must contain body text")
  }

  /**
   * Recipe creation page.
   */
  def neue(
    title: Option[String] = None,
    slug: Option[String] = None,
    ingredients: java.util.List[String] = new java.util.ArrayList[String],
    recipeBody: Option[String] = None

  ) = {
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    html.neue(
      user.id(),
      title.getOrElse(""),
      slug.getOrElse(""),
      ingredients,
      recipeBody.getOrElse(""))
  }

  /**
   * Recipe creation POST handler.
   */
  def create(
    title: String,
    slug: String,
    authorId: Long,
    ingredients: java.util.List[String] = new java.util.ArrayList[String],
    recipeBody: String,
    image: File,
    isPublished: Boolean
  ) = {
    validateRecipe(title, slug, ingredients, recipeBody)
    if (Validation.hasErrors) {
      neue(Some(title), Some(slug), ingredients, Some(recipeBody))
    } else {
      Recipe.create(
        Recipe(title, slug, authorId, recipeBody, isPublished),
        ingredients,
        if (image == null) None else Some(image)
      ).toOptionLoggingError map { recipe =>
        if (isPublished) {
          flash.success("Success! Your recipe has been published.")
          Action(Recipes.show(recipe.authorId, recipe.slug))
        } else {
          flash.success("Success! Your recipe has been saved.")
          Action(Recipes.edit(recipe.id()))
        }
      } getOrElse {
        // TODO: Better error handling here.
        NotFound("There was a problem creating your recipe. Please try again.")
      }
    }
  }

  /**
   * GET request to recipe edit page.
   */
  def edit(
    recipeId: Long,
    title: Option[String] = None,
    slug: Option[String] = None,
    ingredients: java.util.List[String] = new java.util.ArrayList[String],
    recipeBody: Option[String] = None
  ) = {
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    Recipe.getById(recipeId) match {
      case Some(recipe) => {
        if (recipe.authorId != user.id()) {
          flash += ("error" -> "Looks like you tried to edit someone else's recipe. Try forking it instead.")
          Application.index
        } else {
          html.edit(
            user.id(),
            recipeId,
            title.getOrElse(recipe.title),
            slug.getOrElse(recipe.slug),
            if (ingredients.isEmpty) Ingredient.getByRecipeId(recipeId) map { _.name }
            else ingredients,
            recipeBody.getOrElse(recipe.body),
            Image.getBaseUrlByRecipeId(recipeId)
          )
        }
      }
      case None => {
        flash += ("error" -> "The recipe you're trying to edit doesn't exist.")
        Application.index
      }
    }
  }

  /**
   * POST request to recipe edit page.
   */
  def update(
    recipeId: Long,
    title: String,
    slug: String,
    ingredients: java.util.List[String] = new java.util.ArrayList[String],
    recipeBody: String,
    image: File,
    isPublished: Boolean
  ) = {
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    Recipe.getById(recipeId) match {
      case Some(recipe) => {
        if (recipe.authorId != user.id()) {
          flash.error("Looks like you tried to edit someone else's recipe. Try forking it instead.")
          Application.index
        } else {
          validateRecipe(title, slug, ingredients, recipeBody)
          if (Validation.hasErrors) {
            edit(
              recipeId,
              title = if (title.isEmpty) None else Some(title),
              slug = if (slug.isEmpty) None else Some(slug),
              ingredients,
              recipeBody = if (recipeBody.isEmpty) None else Some(recipeBody))
          } else {
            val timestamp = new Timestamp(System.currentTimeMillis())
            val newRecipe = recipe.copy(
              title = title,
              slug = slug,
              modifiedAt = timestamp,
              publishedAt = if (isPublished) Some(timestamp) else None,
              body = recipeBody)
            Recipe.update(newRecipe, ingredients, if (image == null) None else Some(image))

            if (isPublished) {
              flash.success("Success! Your recipe has been published.")
              Action(Recipes.show(recipe.authorId, recipe.slug))
            } else {
              flash.success("Success! Your recipe has been saved.")
              Action(Recipes.edit(recipe.id()))
            }
          }
        }
      }
      case None => {
        flash.error("The recipe you're trying to edit doesn't exist!")
        Application.index
      }
    }
  }

  /**
   * Fork a recipe (i.e. copy it to another user's account).
   */
  def fork(recipeId: Long): Any = {
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    _fork(recipeId, user.id())
  }

  /**
   * Fork a recipe (i.e. copy it to another user's account).
   *
   * recipeId: Id of recipe to fork.
   * userId:   Id of user doing the forking.
   */
  protected[this] def _fork(recipeId: Long, userId: Long) = Recipe.getById(recipeId) match {
    case Some(recipe) => {
      if (recipe.authorId == userId) {
        flash += ("warning" -> "You can't fork your own recipes.")
        Application.index
      } else if (Recipe.getByAuthorIdAndSlug(userId, recipe.slug).isDefined) {
        flash += ("warning" -> "You can't fork that recipe because you already have one by the same name.")
        _show(recipeId)
      } else {
        val timestamp = new Timestamp(System.currentTimeMillis())
        Recipe.create(
          recipe.copy(
            id = play.db.anorm.NotAssigned,
            authorId = userId,
            createdAt = timestamp,
            modifiedAt = timestamp,
            parentRecipe = Some(recipeId)
          ),
          Ingredient.getByRecipeId(recipeId) map { _.name }
        ).toOptionLoggingError map { newRecipe =>
          flash.success("Successfully forked " + recipe.title + "!")
          Action(Recipes.show(newRecipe.authorId, newRecipe.slug))
        } getOrElse {
          // TODO: Better error handling here.
          NotFound("There was a problem while forking this recipe. Please try again.")
        }
      }
    }
    case None => {
      flash += ("error" -> "The recipe you're trying to fork doesn't exist.")
      Application.index
    }
  }

  /**
   * Recipe deletion POST handler.
   */
  def delete(recipeId: Long) = {
    val user = GingrsnapUser.getByEmail(session.get("username")).get
    Recipe.getById(recipeId) match {
      case Some(recipe) => {
        if (recipe.authorId != user.id()) {
          flash += ("warning" -> "You can't delete recipes that aren't yours.")
          _show(recipeId)
        } else {
          Recipe.delete(recipeId)
          flash.success("Successfully deleted " + recipe.title + ".")
          Application.index
        }
      }
      case None => {
        flash += ("error" -> "The recipe you're trying to delete doesn't exist.")
        Application.index
      }
    }
  }

  /**
   * Look up and show a recipe by recipeId.
   */
  protected[this] def _show(recipeId: Long): java.lang.Object = Recipe.getById(recipeId) match {
    case Some(recipe) => show(recipe.authorId, recipe.slug)
    case None => NotFound("No such recipe")
  }

  /**
   * Look up and show a recipe by userId and recipe slug.
   */
  @NonSecure def show(userId: Long, slug: String) = {
    val connectedUser = GingrsnapUser.getByEmail(session.get("username"))

    // Store request url so we can redirect back in case user subsequently logs in.
    flash.put("url", request.url)

    Recipe.getByAuthorIdAndSlug(userId, slug) map { case (recipe, ingredients) =>
      val totalMakeCount = Make.getCountByRecipeId(recipe.id())
      val userMakeCountOpt = connectedUser map { u =>
        Make.getCountByUserAndRecipe(u.id(), recipe.id())
      }
      val isMakable = connectedUser map { u: GingrsnapUser =>
        Recipe.isMakable(u.id(), recipe.id())
      } getOrElse(false)

      html.show(
        recipe.id(),
        recipe.title,
        userId,
        ingredients map { _.name },
        recipe.body,
        Image.getBaseUrlByRecipeId(recipe.id()),
        totalMakeCount,
        userMakeCountOpt,
        isMakable,
        connectedUser)
    } getOrElse {
      NotFound("No such recipe")
    }
  }
}
