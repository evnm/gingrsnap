package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class Tip(
  id: Pk[Long],
  userId: Long,
  recipeId: Long,
  createdAt: Timestamp,
  body: String
)

object Tip extends Magic[Tip] with Timestamped[Tip] {
  def apply(userId: Long, recipeId: Long, body: String) = {
    new Tip(NotAssigned, userId, recipeId, new Timestamp(System.currentTimeMillis()), body)
  }

  override def create(tip: Tip) = {
    super.create(tip) map { createdTip =>
      Event.create(
        Event(EventType.TipLeave.id, createdTip.userId, createdTip.recipeId)
      )
      createdTip
    }
  }

  /**
   * Hydrates a Tip into a renderable tuble of (tip, user who left tip).
   */
  def hydrate(tip: Tip) = (tip, GingrsnapUser.getById(tip.userId).get)

  /**
   * Returns true if user can leave a tip on a given recipe. (i.e. they haven't
   * done so before within a given timeframe)
   */
  def isAllowed(userId: Long, recipeId: Long) = {
    import controllers.Constants.TipCreatedAtThreshold

    Tip.getMostRecent(userId, recipeId) match {
      case Some(tip) => {
        val now = new Timestamp(System.currentTimeMillis())
        // Don't allow if the user has made this recipe within the last
        // TipCreatedAtThreshold milliseconds.
        now.getTime - tip.createdAt.getTime > TipCreatedAtThreshold
      }
      case None => true
    }
  }

  /**
   * Gets a user's most recent Tip for a given recipe.
   */
  def getMostRecent(userId: Long, recipeId: Long): Option[Tip] = {
    SQL("""
        select * from Tip
        where userId = {userId} and recipeId = {recipeId}
        order by createdAt desc
        limit 1
        """)
      .on("userId" -> userId, "recipeId" -> recipeId)
      .as(Tip ?)
  }

  /**
   * Gets all of the tips associated with a given recipe.
   */
  def getByRecipeId(recipeId: Long): Seq[Tip] = {
    SQL("""
        select * from Tip
        where recipeId = {recipeId}
        order by createdAt desc
        """)
      .on("recipeId" -> recipeId)
      .as(Tip *)
  }
}
