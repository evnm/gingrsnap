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

object Ingredient extends Magic[Ingredient] {
  override def extendExtractor[C](f:(Manifest[C] =>
    Option[ColumnTo[C]]), ma:Manifest[C]):Option[ColumnTo[C]] = (ma match {
    case m if m == Manifest.classType(classOf[Timestamp]) =>
      Some(rowToTimestamp)
    case _ => None
  }).asInstanceOf[Option[ColumnTo[C]]]

  def rowToTimestamp: Column[Timestamp] = {
    Column[Timestamp](transformer = { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case time:java.sql.Timestamp => Right(time)
        case _ => Left(TypeDoesNotMatch("Cannot convert " + value + " to Timestamp for column " + qualified))
      }
    })
  }

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
