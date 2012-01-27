package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import scala.reflect.Manifest

case class Ingredient(
  id: Pk[Long],
  name: String,
  recipeId: Long,
  createdAt: Timestamp
)

object Ingredient extends Magic[Ingredient] with Timestamped[Ingredient] {
  def apply(name: String, recipeId: Long) =
    new Ingredient(NotAssigned, name, recipeId, new Timestamp(System.currentTimeMillis()))

  /**
   * Create a set of ingredients associated with a given recipe.
   */
  def createAllByRecipeId(recipeId: Long, ingredients: Seq[String]) = {
    for (ingr <- ingredients) {
      // Because of a bug in Play's form data parsing, some of these ingredients
      // may be null.
      if (ingr != null)
        create(Ingredient(ingr, recipeId))
    }
  }

  /**
   * Deletes all recipes associated with a given recipe. Returns true if
   * successful, false otherwise.
   */
  def deleteByRecipeId(recipeId: Long): Boolean = {
    SQL("delete from Ingredient where recipeId = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute()
  }

  /**
   * Gets all of the ingredients associated with a given recipe.
   */
  def getByRecipeId(recipeId: Long): Seq[Ingredient] = {
    Ingredient.find("recipeId = {recipeId}").on("recipeId" -> recipeId).list()
  }
}
