package controllers

import models.{Event, Feature, Recipe, GingrsnapUser}
import play._
import play.cache.Cache
import play.mvc.Controller

object Application extends BaseController {
  import views.Application._

  /**
   * Annotating return type and nvoking GingrsnapUsers.home directly because
   * Application.index and GingrsnapUsers.home are mutually recursive.
   */
  def index: templates.Html = Authentication.getLoggedInUser match {
    case Some(user) =>
      // Only show /recipes/following if results are non-empty.
      if (Feature(Constants.UserFollowing) && Recipe.getMostRecentFollowed(user.id(), 1).nonEmpty) {
        GingrsnapUsers.followingRecipes
      } else {
        GingrsnapUsers.globalRecipes
      }
    case None => {
      html.index(
        Recipe.getMostRecent(8) map { Recipe.hydrate(_) }
      )
    }
  }

  def about = {
    html.about(Authentication.getLoggedInUser.isDefined)
  }

  def robots = Text("""User-agent: *
Disallow: /makes/*
Disallow: /follows/*
Disallow: /events/*
Disallow: /recipes/*
Disallow: /tips/*
Disallow: /account/*?
Disallow: /feedback
Disallow: /oauth/*
"""
  )

  def humans = Text("""/* TEAM */
Evan Meagher
http://evanmeagher.net
@evanm
San Francisco, CA

/* THANKS */
Dustin Richmond: @darichmond
Alexa Rhoads: @lexr
Kelly Dunn: @kellyleland
"""
  )
}
