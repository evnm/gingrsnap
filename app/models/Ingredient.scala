package models

import java.util.Date
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class Ingredient(
  id: Pk[Long],
  name: String,
  createdAt: Date
)

object Ingredient extends Magic[Ingredient] {
  def apply(name: String) = new Ingredient(NotAssigned, name, new Date())

  /**
   * Gets all of the ingredients associated with a given recipe.
   */
  def getByRecipeId(recipeId: Long): Seq[Ingredient] = {
    Seq()
  }
}
