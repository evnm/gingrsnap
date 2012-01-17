package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr
import scala.reflect.Manifest

case class Make(
  id: Pk[Long],
  userId: Long,
  recipeId: Long,
  createdAt: Timestamp
)

object Make extends Magic[Make] {
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

  def apply(userId: Long, recipeId: Long) = {
    new Make(NotAssigned, userId, recipeId, new Timestamp(System.currentTimeMillis()))
  }

  override def create(make: Make) = {
    super.create(make) map { createdMake =>
      // Create a RecipeCreation event.
      Event.create(
        Event(EventType.RecipeMake.id, createdMake.userId, createdMake.recipeId)
      )
      createdMake
    }
  }

  /**
   * Gets a user's most recent Make of a given recipe.
   */
  def getMostRecent(userId: Long, recipeId: Long): Option[Make] = {
    SQL("""
        select * from Make m
        where m.userId = {userId} and m.recipeId = {recipeId}
        order by createdAt desc
        limit 1
        """)
      .on("userId" -> userId, "recipeId" -> recipeId)
      .as(Make ?)
  }

  /**
   * Get the total make count for a given fecipe.
   */
  def getCountByRecipeId(recipeId: Long): Long = {
    Make.count("recipeId = {recipeId}")
      .on("recipeId" -> recipeId)
      .single()
  }

  /**
   * Get a user's total make count.
   */
  def getCountByUserId(userId: Long): Long = {
    Make.count("userId = {userId}")
      .on("userId" -> userId)
      .single()
  }

  /**
   * Get a user's made count for a given recipe.
   */
  def getCountByUserAndRecipe(userId: Long, recipeId: Long): Long = {
    Make.count("userId = {userId} and recipeId = {recipeId}")
      .on("userId" -> userId, "recipeId" -> recipeId)
      .single()
  }
}
