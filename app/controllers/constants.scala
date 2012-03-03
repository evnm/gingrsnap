package controllers

// TODO: Put these somewhere else. Conf?
object Constants {
  // Twitter OAuth-dance cache keys.
  val TwIfaceCacheKey = "twitterIface"
  val TwRequestTokenCacheKey = "twRequestToken"

  val GingrsnapUserObjKey = "userObj"
  val RecipeObjKey = "recipeObj"
  val EncryptedEmailToUserIdKey = "encryptedEmailToUserId"
  val SlugToUserIdKey = "slugToUserId"
  val AccountObjKey = "accountObj"
  val FeatureKey = "feature"

  // Thresholds, in millisends. (i.e. enforced time differences between
  // makes, tips)
  val MakeCreatedAtThreshold = 21600000
  val TipCreatedAtThreshold = 21600000

  /**
   * Feature keys
   */
  // Recipe-forking, a la repos on GitHub.
  val Forking = "forking"

  // Ability to leave tips for recipes.
  val RecipeTips = "recipe-tips"

  // Tweet and Facebook like buttons on recipe pages.
  val TweetButtons = "tweet-buttons"
  val FbLikeButtons = "fb-like-buttons"

  // Ability to follow other Gingrsnap users.
  val UserFollowing = "user-following"

  // Event feeds of people you follow on Twitter.
  val TwitterEventFeeds = "twitter-event-feeds"
}
