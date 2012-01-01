package models

import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr
import scala.reflect.Manifest

case class Recipe(
  id: Pk[Long],
  title: String,
  slug: String,
  authorId: Long, // TODO: Change to ownerId?
  createdAt: Timestamp,
  modifiedAt: Timestamp,
  publishedAt: Option[Timestamp],
  body: String,
  parentRecipe: Option[Long] = None
)

object Recipe extends Magic[Recipe] {
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

  def apply(
    title: String,
    slug: String,
    authorId: Long,
    body: String,
    isPublished: Boolean
  ) = {
    val timestamp = new Timestamp(System.currentTimeMillis())
    new Recipe(
      NotAssigned,
      title,
      slug,
      authorId,
      createdAt = timestamp,
      modifiedAt = timestamp,
      publishedAt = if (isPublished) Some(timestamp) else None,
      body = body)
  }

  /**
   * Create a recipe with a set of ingredient strings.
   */
  def create(
    recipe: Recipe, ingredients: Seq[String]
  ): MayErr[SqlRequestError, Recipe] = {
    Recipe.create(recipe) flatMap { createdRecipe =>
      Ingredient.createAllByRecipeId(createdRecipe.id(), ingredients)
      MayErr(Right(createdRecipe))
    }
  }

  /**
   * Get the n most-recently published recipes with associated GingrsnapUsers.
   */
  def getMostRecentWithAuthors(n: Int): Seq[(Recipe, GingrsnapUser)] = getMostRecent(n) map { recipe =>
    (recipe, GingrsnapUser.getById(recipe.authorId).get)
  }

  /**
   * Get the n most-recently published recipes.
   */
  def getMostRecent(n: Int): Seq[Recipe] = {
    SQL("""
        select * from Recipe r
        where r.publishedAt is not null
        order by r.modifiedAt desc
        limit {n}
        """)
      .on("n" -> n)
      .as(Recipe *)
  }

  def getById(recipeId: Long): Option[Recipe] =
    Recipe.find("id = {recipeId}").on("recipeId" -> recipeId).first()

  /**
   * Get all of a user's recipes.
   */
  def getByGingrsnapUserId(userId: Long): Seq[Recipe] =
    Recipe.find("authorId = {userId}").on("userId" -> userId).list()

  /**
   * Looks up a recipe by userId and url slug. Optionally returns the looked-up recipe.
   */
  def getByAuthorIdAndSlug(
    authorId: Long, slug: String
  ): Option[(Recipe, Seq[Ingredient])] = {
    SQL("""
        select * from Recipe r
        join Ingredient i on i.recipeId = r.id
        where r.authorId = {authorId} and r.slug = {slug}
        """)
      .on("authorId" -> authorId, "slug" -> slug)
      .as(Recipe ~< Recipe.spanM(Ingredient) ^^ flatten ?)
  }

  /**
   * Deletes a recipe.
   *
   * TODO: Delete from cache, once recipes are cached.
   */
  def delete(recipeId: Long): Boolean = {
    SQL("delete from Recipe where id = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute()
  }
}
