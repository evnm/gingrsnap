package controllers

import models.{Event, EventFeedType, EventType, Feature}
import play.mvc.Controller
import play.data.validation.Validation
import scala.collection.JavaConversions._

object Events extends Controller {
  /**
   * Fetches the next n events for a given event feed type.
   */
  def getNextPage(eventFeedType: Int, lastTimestamp: String, userId: Long, n: Int) = {
    Validation.required("eventFeedType", eventFeedType)
    Validation.required("lastTimestamp", lastTimestamp)
    Validation.required("n", n)
    Validation.isTrue(
      "userId",
      !((eventFeedType == 1 || eventFeedType == 2) && userId == -1)
    ).message("User id required for that feed type")

    if (Validation.hasErrors) {
      // Just return the text of the first error.
      Json("{\"error\": \"" + Validation.errors()(0) + "\"}")
    } else {
      val events = (EventFeedType(eventFeedType) match {
        case EventFeedType.SingleUser =>
          Event.getNextSingleUserPage(userId, lastTimestamp, n)
        case EventFeedType.GingrsnapFollowing if Feature(Constants.UserFollowing) =>
          Event.getNextFollowedPage(userId, lastTimestamp, n)
        case _ => Event.getNextGlobalPage(lastTimestamp, n)
      }).map {
        Event.toJson(_)
      }

      if (events.isEmpty) {
        Json("{\"events\": []}")
      } else {
        Json("{\"events\": [" + events.mkString(", ") + "]}")
      }
    }
  }
}
