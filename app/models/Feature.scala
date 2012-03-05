package models

import play.cache.Cache
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

/**
 * Class/model to implement feature flags.
 */
case class Feature(
  id: Pk[String],
  state: Boolean
)

object Feature extends Magic[Feature] {
  def cacheKey(id: String) = "feature:" + id

  /**
   * Given an id, returns whether or not the feature is available.
   * Returns false for Invalid ids.
   */
  def apply(id: String): Boolean = {
    lazy val fromDb = Feature.find("id = {id}").on("id" -> id).first()

    Cache.get[java.lang.Boolean](cacheKey(id)) match {
      case Some(cachedState) => cachedState.booleanValue
      case None => fromDb map { feature =>
        Cache.add(cacheKey(id), java.lang.Boolean.valueOf(feature.state), "24h")
        feature.state
      } getOrElse(false)
    }
  }
}
