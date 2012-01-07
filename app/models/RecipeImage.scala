package models

import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class RecipeImage(
  recipeId: Long,
  imageId: Long
)

object RecipeImage extends Magic[RecipeImage] {
  def delete(recipeId: Long, imageId: Long): Boolean = {
    SQL("delete from RecipeImage where recipeId = {recipeId} and imageId = {imageId}")
      .on("recipeId" -> recipeId, "imageId" -> imageId)
      .execute()
  }
}
