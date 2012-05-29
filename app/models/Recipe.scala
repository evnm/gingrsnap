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

/**
 * Used by ajax-pagination to keep track of recipe feed type.
 */
object RecipeFeedType extends Enumeration {
  type RecipeFeedType = Value
  val Global = Value(0)
  val GingrsnapFollowing = Value(1)
  val SingleUser = Value(2)
  val TwitterFollowing = Value(3)
}

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

      //Cache.set(recipeIdCacheKey(createdRecipe.id()), createdRecipe, "6h")
      MayErr(Right(createdRecipe))
    }
  }

  /**
   * Hydrates a recipe into a tuple of
   * (modifiedAt, author, Option[(image baseUrl, image extension)]).
   */
  def hydrate(recipe: Recipe): (Recipe, GingrsnapUser, Option[(String, String)]) = {
    val author = GingrsnapUser.getById(recipe.authorId).get
    val imagePair = Image.getBaseUrlByRecipeId(recipe.id())
    (recipe, author, imagePair)
  }

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
   * prevIsModified is the isModified state of the recipe prior to this update.
   */
  def update(
    recipe: Recipe,
    ingredients: Seq[String],
    imageOpt: Option[File] = None,
    prevModifiedAt: Option[Timestamp]
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
    //Cache.set(recipeIdCacheKey(newRecipe.id()), newRecipe, "6h")

    // Create a RecipeUpdate event if:
    // * This is a draft being published, or
    // * The recipe is published and there hasn't been an identical event recently.
    val lastUpdated = recipe.modifiedAt.getTime
    val lastModified: Long = prevModifiedAt.map { _.getTime } getOrElse(0)
    val threshold = 21600000

    if (!prevModifiedAt.isDefined && recipe.publishedAt.isDefined && (now - lastModified > threshold)) {
      Event.create(
        Event(EventType.RecipePublish.id, recipe.authorId, recipe.id()))
    } else if (recipe.publishedAt.isDefined && (now - lastUpdated > threshold)) {
      Event.create(
        Event(EventType.RecipeUpdate.id, recipe.authorId, recipe.id()))
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
        select * from Recipe
        where publishedAt is not null
        order by modifiedAt desc
        limit {n}
        """)
      .on("n" -> n)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Get the n most-recently published recipes.
   */
  def getMostRecentFollowed(userId: Long, n: Int): Seq[Recipe] = {
    SQL("""
        select distinct r.* from Recipe r
        left outer join Follow f on f.objectid = r.authorId
        where (r.authorId = {userId} or (f.subjectid = {userId} and f.followType = {followType}))
          and r.publishedAt IS NOT NULL
        order by r.modifiedAt desc
        limit {n}
        """)
      .on("userId" -> userId, "followType" -> FollowType.UserToUser.id, "n" -> n)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Renders a JSON representation of a hydrated recipe.
   */
  def toJson(recipe: Recipe): String = {
    val (_, author, imageUrlOpt) = Recipe.hydrate(recipe)

    val resultBase =
      "\"authorFullname\": \"" + author.fullname + "\", \"authorSlug\": \"" + author.slug +
      "\", \"recipeSlug\": \"" + recipe.slug + "\", \"recipeTitle\": \"" + recipe.title + "\""

    val urlPair = imageUrlOpt.map { case (baseUrl, extension) =>
      ", \"recipeImgBaseUrl\": \"" + baseUrl + "\", \"recipeImgExtension\": \"" + extension + "\""
    } getOrElse("")

    "{\"modifiedAt\": \"" + recipe.modifiedAt + "\", " + resultBase + urlPair + "}"
  }

  /**
   * Gets the next page of global results after a given timestamp.
   */
  def getNextGlobalPage(lastTimestamp: String, n: Int): Seq[Recipe] = {
    SQL("""
        select * from Recipe
        where modifiedAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS')
          and publishedAt IS NOT NULL
        order by modifiedAt desc
        limit {n}
        """)
      .on("lastTimestamp" -> lastTimestamp, "n" -> n)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Gets the next page of recipes published by people that a given user
   * follows on Gingrsnap.
   */
  def getNextFollowedPage(userId: Long, lastTimestamp: String, n: Int): Seq[Recipe] = {
    SQL("""
      select distinct r.* from Recipe r
      left outer join Follow f on f.objectId = r.authorId
      where r.modifiedAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS')
        and (r.authorId = {userId} or (f.subjectid = {userId} and f.followType = {followType}))
        and r.publishedAt IS NOT NULL
      order by r.modifiedAt desc
      limit {n}"""
    ).on(
      "userId" -> userId,
      "lastTimestamp" -> lastTimestamp,
      "followType" -> FollowType.UserToUser.id,
      "n" -> n
    ).as(Recipe *) filter { recipe =>
      recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
    }
  }

  /**
   * Gets the next page of recipes related to a given user.
   */
  def getNextSingleUserPage(userId: Long, lastTimestamp: String, n: Int): Seq[Recipe] = {
    SQL("""
        select * from Recipe r
        where r.modifiedAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS')
          and r.publishedAt IS NOT NULL
        and r.authorId = {userId}
        order by r.modifiedAt desc
        limit {n}
        """)
      .on("userId" -> userId, "lastTimestamp" -> lastTimestamp, "n" -> n)
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
    //Cache.get[Recipe](recipeIdCacheKey(recipeId)) orElse {
      Recipe.find("id = {recipeId}").on("recipeId" -> recipeId).first()// map { recipe =>
        //Cache.add(recipeIdCacheKey(recipeId), recipe, "6h")
        //recipe
      //}
    //}

  }

  /**
   * Get all of a user's recipes.
   */
  def getAllByUserId(userId: Long): Seq[Recipe] = {
    SQL("""
        select * from Recipe
        where authorId = {userId}
        order by modifiedAt desc
        """)
      .on("userId" -> userId)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Get all of a user's published recipes.
   */
  def getPublishedByUserId(userId: Long): Seq[Recipe] = {
    SQL("""
        select * from Recipe
        where authorId = {userId} and publishedAt is not null
        order by modifiedAt desc
        """)
      .on("userId" -> userId)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Get all of a user's drafts.
   */
  def getDraftsByUserId(userId: Long): Seq[Recipe] = {
    SQL("""
        select * from Recipe
        where authorId = {userId} and publishedAt is null
        order by modifiedAt desc
        """)
      .on("userId" -> userId)
      .as(Recipe *) filter { recipe =>
        recipe.parentRecipe.isEmpty || Feature(Constants.Forking)
      }
  }

  /**
   * Looks up a recipe by user and recipe slugs. Optionally returns the looked-up recipe.
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
   * Get all recipes followed by a given list.
   */
  def getByListId(listId: Long): Seq[Recipe] = {
    SQL("""
        select * from Recipe r
        join Follow f on f.objectId = r.id
        where f.subjectId = {listId} and f.followType = {followType}
        """)
      .on("listId" -> listId, "followType" -> FollowType.ListToRecipe.id)
      .as(Recipe *)
  }

  /**
   * Deletes a recipe.
   *
   * TODO: Delete from cache, once recipes are cached.
   */
  def delete(recipeId: Long): Boolean = {
    //Cache.delete(recipeIdCacheKey(recipeId))
    SQL("delete from Event where objectId = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute() ||
    SQL("delete from Follow where followType = {followType} and objectId = {recipeId}")
      .on("followType" -> FollowType.ListToRecipe.id, "recipeId" -> recipeId)
      .execute() ||
    SQL("delete from Recipe where id = {recipeId}")
      .on("recipeId" -> recipeId)
      .execute()
  }
}
