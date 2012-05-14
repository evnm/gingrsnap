package models

import controllers.Constants
import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

/**
 * Follow ~= "subject follows object"
 *
 * i.e. "user follows users", "list follows recipe"
 */
case class Follow(
  id: Pk[Long],
  followType: Int,
  subjectId: Long,
  objectId: Long,
  createdAt: Timestamp
)

/**
 * Subject and Object class hierarchies.
 */
sealed trait FollowObject
case class GingrsnapUserFollowObject(obj: GingrsnapUser) extends FollowObject
case class RecipeFollowObject(author: GingrsnapUser, recipe: Recipe) extends FollowObject
// TODO: IngredientObject?

/**
 * Enum value names are of the form <subject type>To<object type>.
 */
object FollowType extends Enumeration {
  type FollowType = Value
  val UserToUser = Value(0)
  val UserToRecipe = Value(1)
  val ListToRecipe = Value(2)
}

object Follow extends Magic[Follow] with Timestamped[Follow] {
  def apply(
    followType: FollowType.Value,
    subjectId: Long,
    objectId: Long
  ) = {
    new Follow(
      NotAssigned,
      followType.id,
      subjectId,
      objectId,
      new Timestamp(System.currentTimeMillis()))
  }

  override def create(follow: Follow) = {
    super.create(follow) map { createdFollow =>
      Event.create(
        Event(EventType.GingrsnapUserFollow.id, createdFollow.subjectId, createdFollow.objectId)
      )
      createdFollow
    }
  }

  /**
   * Hydrates a Follow into a renderable tuple of (subject, object).
   */
  def hydrate(follow: Follow): (Timestamp, GingrsnapUser, FollowType.Value, FollowObject) = {
    // Currently, all follows are "user verbed recipe", so...
    val user = GingrsnapUser.getById(follow.subjectId).get
    val recipe = Recipe.getById(follow.objectId).get
    val followObject = follow.followType match {
      case FollowType.UserToUser => {
        GingrsnapUserFollowObject(
          GingrsnapUser.getById(follow.objectId).get)
      }
      case FollowType.UserToRecipe => {
        (Recipe.getById(follow.objectId) map { recipe =>
          RecipeFollowObject(GingrsnapUser.getById(recipe.authorId).get, recipe)
        }).get
      }
    }

    (
      follow.createdAt,
      user,
      FollowType(follow.followType),
      followObject
    )
  }

  /**
   * Returns true if subject follows object.
   */
  def exists(followType: FollowType.Value, subjectId: Long, objectId: Long): Boolean = {
    Follow.count(
      "followType = {followType} and subjectId = {subjectId} and objectId = {objectId}"
    ).on(
      "followType" -> followType.id,
      "subjectId" -> subjectId,
      "objectId" -> objectId
    ).single() > 0
  }

  def delete(followType: FollowType.Value, subjectId: Long, objectId: Long): Boolean = {
    SQL("""
        delete from Follow
        where followType = {followType} and subjectId = {subjectId} and objectId = {objectId}
        """
    ).on(
      "followType" -> followType.id,
      "subjectId" -> subjectId,
      "objectId" -> objectId
    ).execute()
  }
}
