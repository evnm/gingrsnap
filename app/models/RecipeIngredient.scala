package models

import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class RecipeIngredient(
  recipeId: Long,
  ingredientId: Long
)

object RecipeIngredient extends Magic[RecipeIngredient]
