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
case class GingrsnapUserSubject(subject: GingrsnapUser) extends EventSubject

sealed trait EventObject
case class GingrsnapUserObject(obj: GingrsnapUser) extends EventObject
case class RecipeObject(obj: (Recipe, GingrsnapUser)) extends EventObject

object EventType extends Enumeration {
  type EventType = Value
  val RecipePublish = Value(0)
  val RecipeFork = Value(1)
  val RecipeUpdate = Value(2)
  val RecipeMake = Value(3)
  val TipLeave = Value(4)
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
   * Hydrates an Event into a renderable tuple of (subject, eventtype, object).
   */
  def hydrate(event: Event): (Timestamp, EventSubject, EventType.Value, EventObject) = {
    // Currently, all events are "user verbed recipe", so...
    val user = GingrsnapUser.getById(event.subjectId).get
    val recipe = Recipe.getById(event.objectId).get
    val author = GingrsnapUser.getById(recipe.authorId).get

    (
      event.createdAt,
      GingrsnapUserSubject(user),
      EventType(event.eventType),
      RecipeObject(recipe, author)
    )

    // Eventually, do something like this:
    /*
    EventType(event.eventType) match {
      case eventType @ RecipePublish => (
        GingrsnapUserSubject(GingrsnapUser.getById(event.subjectId)),
        eventType,
        RecipeObject(Recipe.getById(event.objectId))
      )
      ...
    }
    */
  }

  /**
   * Gets the n most recent events across the whole site.
   */
  def getMostRecent(n: Int): Seq[Event] = {
    SQL("""
        select * from Event e
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
}
