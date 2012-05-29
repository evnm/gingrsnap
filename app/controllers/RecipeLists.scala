package controllers

import java.net.URLEncoder
import models.{GingrsnapUser, Recipe, RecipeList}
import play._
import play.data.validation.Validation
import play.db.anorm.SqlRequestError
import play.mvc._
import scala.collection.JavaConversions._
import secure.NonSecure

object RecipeLists extends BaseController with Secure {
  import views.RecipeLists.html

  /**
   * Returns whether or not a list slug is unique for a given user id.
   */
  def slugIsUniqForUserId(slug: String, userId: Long): Boolean = {
    RecipeList.find("slug = {slug} and creatorId = {userId}")
      .on("slug" -> slug, "userId" -> userId)
      .first()
      .isEmpty
  }

  /**
   * Returns a unique list title and slug, which is either the argument slug or
   * the argument slug with a numerical suffix.
   */
  def findUniqTitleAndSlugForUserId(
    title: String, slug: String, userId: Long
  ): (String, String) = {
    if (slugIsUniqForUserId(slug, userId))
      (title, slug)
    else {
      var suffix = 2
      while (!slugIsUniqForUserId(slug + suffix, userId)) {
        suffix += 1
      }
      (title + " " + suffix, slug + suffix)
    }
  }

  def create(
    creatorId: Long = -1,
    title: String = "",
    description: String = ""
  ) = GingrsnapUser.getById(creatorId) match {
    case Some(user) => {
      Validation.isTrue("creatorId", creatorId > 0)
        .message("Invalid user id for list creation")
      Validation.isTrue("title", title.nonEmpty)
        .message("Cannot create a list without a title")

      if (Validation.hasErrors) {
        flash.error(Validation.errors.get(0).message)
      } else {
        val (uniqTitle, slug) =
          findUniqTitleAndSlugForUserId(title, title.toLowerCase.replace(" ", "+"), creatorId)
        val descOpt =
          if (description.isEmpty)
            None
          else
            Some(description)

        RecipeList.create(
          RecipeList(creatorId, uniqTitle, slug, descOpt)
        ).toOptionLoggingError match {
          case Some(_) =>
            flash.success("Created a list called \"%s\"".format(uniqTitle))
          case None =>
            flash.error("Failed to create a list")
        }
      }

      val url = flash.get("url") match {
        case null => "/"
        case url => url
      }
      flash.discard("url")
      Redirect(url)
    }

    case None =>
      NotFound("The user trying to create this list doesn't exist")
  }

  /**
   * RecipeList deletion POST handler.
   */
  def delete(listId: Long) = Authentication.getLoggedInUser match {
    case Some(user) => {
      RecipeList.getById(listId) match {
        case Some(list) => {
          if (list.creatorId != user.id()) {
            flash += ("warning" -> "You can't delete listss that aren't yours")
            show(user.slug, list.slug)
          } else {
            if (!RecipeList.delete(listId)) {
              flash.success("Successfully deleted " + list.title)
              Action(Application.index)
            } else {
              Logger.error("RecipeList.delete failed for listId(%s)".format(listId))
              flash.error("Failed to delete this list")
              show(user.slug, list.slug)
            }
          }
        }
        case None => {
          flash += ("error" -> "The list you're trying to delete doesn't exist")
          Action(Application.index)
        }
      }
    }
    case None => {
      // Should never get here, but redirect just in case.
      // TODO: Add logging.
      Action(Application.index)
    }
  }

  /**
   * Look up and show a recipe list by user and recipe slugs.
   */
  @NonSecure def show(userSlug: String, listSlug: String) = {
    // Store request url so we can redirect back in case user subsequently logs in.
    flash.put("url", request.url)

    GingrsnapUser.getBySlug(userSlug) flatMap { creator =>
      RecipeList.getBySlugs(userSlug, listSlug) map { list =>
        val listUrl = play.configuration("application.baseUrl") +
          URLEncoder.encode(creator.slug, "utf8") + "/" +
          URLEncoder.encode(list.slug, "utf8")
        val recipes = Recipe.getByListId(list.id()) map { Recipe.hydrate(_) }

        html.show(
          list.id(),
          listUrl,
          list.title,
          list.description,
          creator,
          list.createdAt,
          list.modifiedAt,
          recipes,
          Authentication.getLoggedInUser)
      }
    } getOrElse(NotFound("No such list"))
  }
}
