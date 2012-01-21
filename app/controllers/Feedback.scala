package controllers

import notifiers.Mails
import play.mvc.Controller

object Feedback extends Controller {
  def send(feedbackBody: String = "") = Authentication.getLoggedInUser match {
    case Some(user) => Mails.feedback(feedbackBody, user)
    case None => Mails.feedback(feedbackBody)
  }
}
