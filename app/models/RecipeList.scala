package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr

/**
 * A user-curated list of recipes.
 */
case class RecipeList(
  id: Pk[Long],
  creatorId: Long,
  title: String,
  slug: String,
  description: Option[String],
  createdAt: Timestamp,
  modifiedAt: Timestamp
)

object RecipeList extends Magic[RecipeList] with Timestamped[RecipeList] {
  def apply(
    creatorId: Long,
    title: String,
    slug: String,
    description: Option[String] = None
  ) = {
    val timestamp = new Timestamp(System.currentTimeMillis())
    new RecipeList(NotAssigned, creatorId, title, slug, description, timestamp, timestamp)
  }

  override def create(list: RecipeList) = {
    super.create(list) map { createdList =>
      Event.create(
        Event(EventType.RecipeListCreate.id, list.creatorId, createdList.id())
      )
      createdList
    }
  }

  def getById(listId: Long): Option[RecipeList] = {
    RecipeList.find("id = {listId}").on("listId" -> listId).first()
  }

  /**
   * Get all lists created by a given user.
   */
  def getByUserId(userId: Long): Seq[RecipeList] = {
    RecipeList.find("creatorId = {userId}").on("userId" -> userId).list()
  }

  /**
   * Optionally gets a list by list and creator id.
   */
  def getByUserIdAndListId(userId: Long, listId: Long): Option[RecipeList] = {
    RecipeList.find("creatorId = {userId} and id = {listId}")
      .on("userId" -> userId, "listId" -> listId)
      .first()
  }

  /**
   * Get all lists that contain a given recipe.
   */
  def getByRecipeId(recipeId: Long): Seq[RecipeList] = {
    SQL("""
        select * from RecipeList rl
        join Follow f on rl.id = f.subjectId
        join Recipe r on r.id = f.objectId
        where followType = {followType} and r.id = {recipeId}
        """)
      .on("followType" -> FollowType.ListToRecipe.id, "recipeId" -> recipeId)
      .as(RecipeList *)
  }

  /**
   * Looks up a list by user and list slugs. Optionally returns the looked-up list.
   */
  def getBySlugs(userSlug: String, listSlug: String): Option[RecipeList] = {
    SQL("""
        select * from RecipeList rl
        join GingrsnapUser u on u.id = rl.creatorId
        where u.slug = {userSlug} and rl.slug = {listSlug}
        """)
      .on("userSlug" -> userSlug, "listSlug" -> listSlug)
      .as(RecipeList ?)
  }

  /**
   * Deletes a list and all associated follow records.
   */
  def delete(listId: Long): Boolean = {
    SQL("delete from Event where objectId = {listId}")
      .on("listId" -> listId)
      .execute() ||
    SQL("delete from Follow where followType = {followType} and subjectId = {listId}")
      .on("followType" -> FollowType.ListToRecipe.id, "listId" -> listId)
      .execute() ||
    SQL("delete from RecipeList where id = {listId}")
      .on("listId" -> listId)
      .execute()
  }
}
