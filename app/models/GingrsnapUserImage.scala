package models

import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._

case class GingrsnapUserImage(
  userId: Long,
  imageId: Long
)

object GingrsnapUserImage extends Magic[GingrsnapUserImage] {
  def delete(userId: Long, imageId: Long): Boolean = {
    SQL("delete from GingrsnapUserImage where userId = {userId} and imageId = {imageId}")
      .on("userId" -> userId, "imageId" -> imageId)
      .execute()
  }
}
