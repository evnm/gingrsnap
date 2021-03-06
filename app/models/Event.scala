package models

import controllers.Constants
import java.sql.Timestamp
import play.db.anorm._
import play.db.anorm.defaults.Magic
import play.db.anorm.SqlParser._

/**
 * Event ~= "subject verbed object"
 *
 * EventTypes:
 *   * Published a recipe
 *   * Forked a recipe
 *   * Followed a recipe (i.e. made the dish)
 */
case class Event(
  id: Pk[Long],
  eventType: Int,
  subjectId: Long,
  objectId: Long,
  createdAt: Timestamp
)

// Subject, Object, and EventType class hierarchies.
sealed trait EventSubject
case class GingrsnapUserEventSubject(subject: GingrsnapUser) extends EventSubject

sealed trait EventObject
case class GingrsnapUserEventObject(obj: GingrsnapUser) extends EventObject
case class RecipeEventObject(obj: (Recipe, GingrsnapUser)) extends EventObject
case class RecipeListEventObject(obj: RecipeList) extends EventObject

object EventType extends Enumeration {
  type EventType = Value
  val RecipePublish = Value(0)
  val RecipeFork = Value(1)
  val RecipeUpdate = Value(2)
  val RecipeMake = Value(3)
  val TipLeave = Value(4)
  val GingrsnapUserFollow = Value(5)
  val RecipeFollow = Value(6)
  val RecipeListCreate = Value(7)
  val RecipeAddToList = Value(8)
}

/**
 * Used by ajax-pagination to keep track of event feed type.
 */
object EventFeedType extends Enumeration {
  type EventFeedType = Value
  val Global = Value(0)
  val GingrsnapFollowing = Value(1)
  val SingleUser = Value(2)
  val TwitterFollowing = Value(3)
}

object Event extends Magic[Event] with Timestamped[Event] {
  def apply(
    eventType: Int,
    subjectId: Long,
    objectId: Long
  ) = {
    new Event(
      NotAssigned,
      eventType,
      subjectId,
      objectId,
      new Timestamp(System.currentTimeMillis()))
  }

  /**
   * Renders a JSON representation of a hydrated event.
   */
  def toJson(event: Event): String = {
    val (createdAt, eventSubject, thumbnailUrlOpt, eventType, eventObject) = Event.hydrate(event)

    val result =  (eventSubject, eventObject) match {
      case (GingrsnapUserEventSubject(subject), RecipeEventObject((recipe, author))) => {
        "\"subjectFullname\": \"" + subject.fullname + "\", \"subjectSlug\": \"" + subject.slug +
        "\", \"recipeSlug\": \"" + recipe.slug + "\", \"authorSlug\": \"" + author.slug +
        "\", \"recipeTitle\": \"" + recipe.title + "\""
      }

      case (GingrsnapUserEventSubject(subject), GingrsnapUserEventObject(obj)) => {
        "\"subjectFullname\": \"" + subject.fullname + "\", \"subjectSlug\": \"" + subject.slug +
        "\", \"objFullname\": \"" + obj.fullname + "\", \"objSlug\": \"" + obj.slug + "\""
      }

      case (GingrsnapUserEventSubject(subject), RecipeListEventObject(list)) => {
        /*val recipePart = recipeOpt map { recipe =>
          ", \"recipeTitle\": \"" + recipe.title + "\", \"recipeSlug\": \"" + recipe.slug
        } getOrElse("")*/

        "\"subjectFullname\": \"" + subject.fullname + "\", \"subjectSlug\": \"" + subject.slug +
        "\", \"listTitle\": \"" + list.title + "\", \"listSlug\": \"" + list.slug + "\""
      }
    }

    val thumbnailUrlStr = thumbnailUrlOpt.map { url =>
      ", \"thumbnailUrl\": \"" + url + "\""
    } getOrElse("")

    "{\"eventType\": \"" + event.eventType + "\", \"createdAt\": \"" + event.createdAt +
    "\", " + result + thumbnailUrlStr + "}"
  }

  /**
   * Hydrates an Event into a renderable tuple of
   * (timestamp, subject, image thumbnail url, eventtype, object).
   */
  def hydrate(event: Event): (Timestamp, EventSubject, Option[String], EventType.Value, EventObject) = {
    val user = GingrsnapUser.getById(event.subjectId).get
    val userThumbnailOpt = Image.getBaseUrlByUserId(user.id()) map { case (baseUrl, extension) =>
      baseUrl + "_thumbnail." + extension
    }

    EventType(event.eventType) match {
      case EventType.GingrsnapUserFollow => {
        val obj = GingrsnapUser.getById(event.objectId).get

        (
          event.createdAt,
          GingrsnapUserEventSubject(user),
          userThumbnailOpt,
          EventType(event.eventType),
          GingrsnapUserEventObject(obj)
        )
      }

      case EventType.RecipeListCreate => {
        val list = RecipeList.getById(event.objectId).get

        (
          event.createdAt,
          GingrsnapUserEventSubject(user),
          userThumbnailOpt,
          EventType(event.eventType),
          RecipeListEventObject(list)
        )
      }

      case _ => {
        val recipe = Recipe.getById(event.objectId).get
        val author = GingrsnapUser.getById(recipe.authorId).get

        (
          event.createdAt,
          GingrsnapUserEventSubject(user),
          userThumbnailOpt,
          EventType(event.eventType),
          RecipeEventObject(recipe, author)
        )
      }
    }
  }

  /**
   * Gets the n most recent events related to a user's set of followed entities.
   */
  def getMostRecentFollowed(userId: Long, n: Int): Seq[Event] = {
    SQL("""
        select distinct e.* from Event e
        left outer join Follow f on f.objectid = e.subjectid
        where (e.subjectId = {userId} or f.subjectid = {userId}) and e.eventType != 5
        order by e.createdAt desc
        limit {n}
        """)
      .on("userId" -> userId, "n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }

  /**
   * Gets the n most recent events across the whole site.
   */
  def getMostRecent(n: Int): Seq[Event] = {
    SQL("""
        select * from Event e
        where e.eventType != 5
        order by createdAt desc
        limit {n}
        """)
      .on("n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }

  /**
   * Gets the n most recent events related to a given user.
   */
  def getMostRecentByUserId(userId: Long, n: Int): Seq[Event] = {
    SQL("""
        select * from Event
        where subjectId = {userId}
        order by createdAt desc
        limit {n}
        """)
      .on("userId" -> userId, "n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }

  /**
   * Gets the next page of global results after a given timestamp.
   */
  def getNextGlobalPage(lastTimestamp: String, n: Int): Seq[Event] = {
    SQL("""
        select * from Event
        where createdAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS') and eventType != 5
        order by createdAt desc
        limit {n}
        """)
      .on("lastTimestamp" -> lastTimestamp, "n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }

  /**
   * Gets the next page of events related to entities that a given user follows
   * on Gingrsnap.
   */
  def getNextFollowedPage(userId: Long, lastTimestamp: String, n: Int): Seq[Event] = {
    SQL("""
        select distinct e.* from Event e
        left outer join Follow f on f.objectid = e.subjectid
        where e.createdAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS') and e.eventType != 5
        and (e.subjectId = {userId} or f.subjectid = {userId})
        order by e.createdAt desc
        limit {n}
        """)
      .on("userId" -> userId, "lastTimestamp" -> lastTimestamp, "n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }

  /**
   * Gets the next page of events related to a given user.
   */
  def getNextSingleUserPage(userId: Long, lastTimestamp: String, n: Int): Seq[Event] = {
    SQL("""
        select * from Event e
        where e.createdAt < to_timestamp({lastTimestamp}, 'YYYY-MM-DD HH24:MI:SS.MS')
        and e.subjectId = {userId}
        order by e.createdAt desc
        limit {n}
        """)
      .on("userId" -> userId, "lastTimestamp" -> lastTimestamp, "n" -> n)
      .as(Event *) filter { event =>
        !(
          !Feature(Constants.Forking) && event.eventType == EventType.RecipeFork.id ||
          !Feature(Constants.RecipeTips) && event.eventType == EventType.TipLeave.id
        )
      }
  }
}
