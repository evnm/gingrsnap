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
    Cache.get[Boolean](cacheKey(id)) getOrElse {
      val result = Feature.find("id = {id}").on("id" -> id).first() match {
        case Some(feature) => feature.state
        case None => false
      }
      Cache.add(cacheKey(id), result, "24h")
      result
    }
  }
}
