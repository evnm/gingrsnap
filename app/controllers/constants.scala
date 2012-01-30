package controllers

// TODO: Put these somewhere else. Conf?
object Constants {
  // Twitter OAuth-dance cache keys.
  val TwIfaceCacheKey = "twitterIface"
  val TwRequestTokenCacheKey = "twRequestToken"

  val GingrsnapUserObjKey = "userObj"
  val EncryptedEmailToUserIdKey = "encryptedEmailToUserId"
  val SlugToUserIdKey = "slugToUserId"
  val AccountObjKey = "accountObj"

  // Thresholds, in millisends. (i.e. enforced time differences between
  // makes, tips)
  val MakeCreatedAtThreshold = 21600000
  val TipCreatedAtThreshold = 21600000

  // Feature keys
  val Forking = "forking"
  val RecipeTips = "recipe-tips"
}
