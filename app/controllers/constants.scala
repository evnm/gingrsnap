package controllers

// TODO: Put these somewhere else. Conf?
object Constants {
  // Twitter OAuth-dance cache keys.
  val TwIfaceCacheKey = "twitterIface"
  val TwRequestTokenCacheKey = "twRequestToken"
  val TwAccessTokenCacheKey = "twAccessToken"
  val TwUserObjCacheKey = "twGingrsnapUserObj"

  val GingrsnapUserObjKey = "userObj"
  val EncryptedEmailToUserIdKey = "encryptedEmailToUserId"
  val AccountObjKey = "accountObj"

  // Make threshold, in millisends. (i.e. enforced time difference between
  // makes.
  val MakeCreatedAtThreshold = 21600000
}
