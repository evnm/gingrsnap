package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr

case class Make(
  id: Pk[Long],
  userId: Long,
  recipeId: Long,
  createdAt: Timestamp
)

object Make extends Magic[Make] with Timestamped[Make] {
  def apply(userId: Long, recipeId: Long) = {
    new Make(NotAssigned, userId, recipeId, new Timestamp(System.currentTimeMillis()))
  }

  override def create(make: Make) = {
    super.create(make) map { createdMake =>
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
