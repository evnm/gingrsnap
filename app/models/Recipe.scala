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
  authorId: Long, // TODO: Change to ownerId?
  createdAt: Date,
  modifiedAt: Date,
  body: String,
  parentRecipe: Option[Long] = None
)

object Recipe extends Magic[Recipe] {
  def apply(
    title: String,
    slug: String,
    authorId: Long,
    body: String
  ) = {
    val date = new Date()
    new Recipe(NotAssigned, title, slug, authorId, date, date, body)
  }

  /**
   * Create a recipe with a set of ingredients.
   */
  def create(
    recipe: Recipe, ingredients: Seq[String]
  ): MayErr[SqlRequestError, Recipe] = {
    Recipe.create(recipe) flatMap { createdRecipe =>
      for (ingr <- ingredients) {
        Ingredient.create(Ingredient(ingr, createdRecipe.id()))
      }
      MayErr(Right(createdRecipe))
    }
  }

  /**
   *
   */

  /**
   * Get the n most recently-posted recipes.
   */
  def getMostRecent(n: Int): Seq[Recipe] = {
    SQL("""
        select * from Recipe r
        order by r.modifiedAt desc
        limit {n}
        """)
      .on("n" -> n)
      .as(Recipe *)
  }

  def getById(recipeId: Long): Option[Recipe] =
    Recipe.find("id = {recipeId}").on("recipeId" -> recipeId).first()

  /**
   * Get all of a user's recipes.
   */
  def getByUserId(userId: Long): Seq[Recipe] =
    Recipe.find("authorId = {userId}").on("userId" -> userId).list()

  /**
   * Looks up a recipe by userId and url slug. Optionally returns the looked-up recipe.
   */
  def getByAuthorIdAndSlug(
    authorId: Long, slug: String
  ): Option[(Recipe, Seq[Ingredient])] = {
    SQL("""
        select * from Recipe r
        join Ingredient i on i.recipeId = r.id
        where r.authorId = {authorId} and r.slug = {slug}
        """)
      .on("authorId" -> authorId, "slug" -> slug)
      .as(Recipe ~< Recipe.spanM(Ingredient) ^^ flatten ?)
  }
}
