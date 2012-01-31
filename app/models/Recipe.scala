package models

import controllers.Constants
import java.io.File
import java.sql.Timestamp
import play.cache.Cache
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.utils.Scala.MayErr

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

object Recipe extends Magic[Recipe] with Timestamped[Recipe] {
  protected[this] def recipeIdCacheKey(recipeId: Long) =
    Constants.RecipeObjKey + ":" + recipeId

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
   * Create a recipe with a set of ingredient strings and an optional image file.
   */
  def create(
    recipe: Recipe,
    ingredients: Seq[String],
    imageOpt: Option[File] = None
  ): MayErr[SqlRequestError, Recipe] = {
    Recipe.create(recipe) flatMap { createdRecipe =>
      // Store the image, if provided.
      imageOpt map { image =>
        Image.create(image) map { createdImage =>
          RecipeImage.create(RecipeImage(createdRecipe.id(), createdImage.id()))
        }
      }
      Ingredient.createAllByRecipeId(createdRecipe.id(), ingredients)

      if (recipe.publishedAt.isDefined) {
        // Create a RecipePublish event.
        Event.create(Event(EventType.RecipePublish.id, createdRecipe.authorId, createdRecipe.id()))
      }

      Cache.set(recipeIdCacheKey(createdRecipe.id()), createdRecipe, "6h")
      MayErr(Right(createdRecipe))
    }
  }

  /**
   * Hydrates a recipe into a tuple of (recipe, author).
   */
  def hydrate(recipe: Recipe) = (recipe, GingrsnapUser.getById(recipe.authorId))

  /**
   * Fork a recipe (i.e. make a copy with a different authorId).
   */
  def fork(recipe: Recipe, userId: Long): MayErr[SqlRequestError, Recipe] = {
    val timestamp = new Timestamp(System.currentTimeMillis())
    Recipe.create(
      recipe.copy(
        id = play.db.anorm.NotAssigned,
        authorId = userId,
        createdAt = timestamp,
        modifiedAt = timestamp,
        publishedAt = Some(timestamp),
        parentRecipe = Some(recipe.id())
      )
    ) flatMap { createdRecipe =>
      Ingredient.createAllByRecipeId(
        createdRecipe.id(),
        Ingredient.getByRecipeId(recipe.id()) map { _.name }
      )

      // Create a RecipeFork event.
      Event.create(
        Event(EventType.RecipeFork.id, userId, createdRecipe.id()))

      MayErr(Right(createdRecipe))
    }
  }

  /**
   * Update a recipe, replacing the ingredient list and optionally its associated image.
   *
   * prevIsPublished is the isPublished state of the recipe prior to this update.
   */
  def update(
    recipe: Recipe,
    ingredients: Seq[String],
    imageOpt: Option[File] = None,
    prevIsPublished: Boolean
  ): Unit = {
    // Replace recipe's ingredient list.
    Ingredient.deleteByRecipeId(recipe.id())
    Ingredient.createAllByRecipeId(recipe.id(), ingredients)

    imageOpt map { imageFile =>
      // Delete old image if one exists.
      Image.getByRecipeId(recipe.id()) map { oldImage =>
        Image.delete(oldImage)
        RecipeImage.delete(recipe.id(), oldImage.id())
      }
      Image.create(imageFile) map { createdImage =>
        RecipeImage.create(RecipeImage(recipe.id(), createdImage.id()))
      }
    }

    val now = System.currentTimeMillis
    val newRecipe = recipe.copy(modifiedAt = new Timestamp(now))
    Recipe.update(newRecipe)
    Cache.set(recipeIdCacheKey(newRecipe.id()), newRecipe, "6h")

    // Create a RecipeUpdate event if the recipe is published and there hasn't
    // been an identical event recently.
    val lastUpdated = recipe.modifiedAt.getTime
    if (recipe.publishedAt.isDefined && (now - lastUpdated > 21600000)) {
      val eventType = if (prevIsPublished) EventType.RecipeUpdate else EventType.RecipePublish
      Event.create(
        Event(eventType.id, recipe.authorId, recipe.id()))
    }
  }

  /**
   * Return whether or not a recipe is currently makable by a given user.
   *
   * e.g. If they've made too recently, they can't make it again.
   */
  def isMakable(userId: Long, recipeId: Long): Boolean = {
    Make.getMostRecent(userId, recipeId) match {
      case Some(make) => {
        val now = new Timestamp(System.currentTimeMillis())
        // Don't allow if the user has made this recipe within the last
        // MakeCreatedAtThreshold milliseconds.
        now.getTime - make.createdAt.getTime > Constants.MakeCreatedAtThreshold
      }
      case None => true
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
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * For a given recipe id, returns an optional hydrated tuple of (recipe, author).
   */
  def getHydratedById(recipeId: Long): Option[(Recipe, GingrsnapUser)] = {
    Recipe.getById(recipeId) flatMap { recipe =>
      GingrsnapUser.getById(recipe.authorId) map { user =>
        (recipe, user)
      }
    }
  }

  def getById(recipeId: Long): Option[Recipe] = {
    Cache.get[Recipe](recipeIdCacheKey(recipeId)) orElse {
      Recipe.find("id = {recipeId}").on("recipeId" -> recipeId).first() map { recipe =>
        Cache.add(recipeIdCacheKey(recipeId), recipe, "6h")
        recipe
      }
    }

  }

  /**
   * Get all of a user's recipes.
   */
  def getByUserId(userId: Long): Seq[Recipe] = {
    Recipe.find("authorId = {userId}").on("userId" -> userId).list() filter { recipe =>
      recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
    }
  }

  /**
   * Looks up a recipe by userId and url slug. Optionally returns the looked-up recipe.
   */
  def getBySlugs(
    userSlug: String, recipeSlug: String
  ): Option[(Recipe, Seq[Ingredient])] = {
    SQL("""
        select * from Recipe r
        join Ingredient i on i.recipeId = r.id
        join GingrsnapUser u on u.id = r.authorId
        where u.slug = {userSlug} and r.slug = {recipeSlug}
        """)
      .on("userSlug" -> userSlug, "recipeSlug" -> recipeSlug)
      .as(Recipe ~< Recipe.spanM(Ingredient) ^^ flatten ?)
  }

  /**
   * Deletes a recipe.
   *
   * TODO: Delete from cache, once recipes are cached.
   */
  def delete(recipeId: Long): Boolean = {
    Cache.delete(recipeIdCacheKey(recipeId))
    SQL("delete from Event where objectId = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute()
    SQL("delete from Recipe where id = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute()
  }
}
