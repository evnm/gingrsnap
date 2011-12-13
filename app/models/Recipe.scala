package models

import java.util.Date
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr

case class Recipe(
  id: Pk[Long],
  title: String,
  slug: String,
  authorId: Long,
  createdAt: Date,
  body: String
)

object Recipe extends Magic[Recipe] {
  def apply(
    title: String,
    slug: String,
    authorId: Long,
    body: String
  ) = new Recipe(NotAssigned, title, slug, authorId, new Date(), body)

  /**
   * Create a recipe with a set of ingredients.
   */
  def create(
    recipe: Recipe, ingredients: Seq[String]
  ): MayErr[SqlRequestError, Recipe] = {
    Recipe.create(recipe) flatMap { createdRecipe =>
      for (ingr <- ingredients) {
        Ingredient.create(Ingredient(ingr)) flatMap { createdIngr =>
          RecipeIngredient.create(RecipeIngredient(createdRecipe.id(), createdIngr.id()))
        }
      }
      MayErr(Right(createdRecipe))
    }
  }

  /**
   * Get all of a user's recipes.
   */
  def getByUserId(userId: Long) =
    Recipe.find("authorId = {userId}").on("userId" -> userId).list()

  /**
   * Looks up a recipe by userId and url slug. Optionally returns the looked-up recipe.
   */
  def getByAuthorIdAndSlug(authorId: Long, slug: String) = {
    SQL("""
        select * from Recipe r
        join RecipeIngredient ri on r.id = ri.recipeId
        join Ingredient i on ri.ingredientId = i.id
        where r.authorId = {authorId} and r.slug = {slug}
        """)
      .on("authorId" -> authorId, "slug" -> slug)
      .as(Recipe ~< Recipe.spanM(Ingredient) ^^ flatten ?)
  }



  /**
   * Looks up a recipe by userId and recipeId. Optionally returns the looked-up recipe.
   */
  def getByAuthorIdAndRecipeId(authorId: Long, recipeId: Long) = {
    SQL("""
        select * from Recipe r
        join RecipeIngredient ri on r.id = ri.recipeId
        join Ingredient i on ri.ingredientId = i.id
        where r.authorId = {authorId} and r.id = {recipeId}
        """)
      .on("authorId" -> authorId, "recipeId" -> recipeId)
      .as(Recipe ~< Recipe.spanM(Ingredient) ^^ flatten ?)
  }

}
