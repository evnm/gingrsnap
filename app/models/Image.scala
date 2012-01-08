package models

import com.amazonaws.services.s3.model.{
  CannedAccessControlList, DeleteObjectRequest, PutObjectRequest}
import java.io.File
import java.util.UUID
import play.db.anorm._
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.libs.Images
import play.utils.Scala.MayErr
import s3.S3
import scala.collection.JavaConversions._

case class Image(
  id: Pk[Long],
  s3Key: String
)

object Image extends Magic[Image] {
  def create(file: File) = {
    val key = UUID.randomUUID().toString()
    val thumbnail = File.createTempFile("thumbnail", null)
    Images.resize(file, thumbnail, 296, -1)
    Seq(
      ("original", file),
      ("thumbnail", thumbnail)
    ) foreach { case (size, file) =>
      // Upload the file to S3.
      S3.client.putObject(
        new PutObjectRequest(S3.bucket, "image/" + key + "_" + size, file)
          .withCannedAcl(CannedAccessControlList.PublicRead))
    }

    // Store Image object in db.
    super.create(Image(NotAssigned, "image/" + key))
  }

  /**
   * Deletes an image by key in S3.
   */
  def delete(image: Image): Boolean = {
    Seq("original", "thumbnail") foreach { size =>
      S3.client.deleteObject(S3.bucket, image.s3Key + "_" + size)
    }

    SQL("delete from Image where id = {imageId}")
      .on("imageId" -> image.id())
      .execute()
  }

  /**
   * Get a photo associated with a user by the id of the user.
   */
  def getByUserId(userId: Long): Option[Image] = {
    SQL("""
        select * from Image i
        join GingrsnapUserImage ri on i.id = ri.imageId
        join GingrsnapUser r on ri.userId = r.id
        where r.id = {userId}
        """)
      .on("userId" -> userId)
      .as(Image ?)
  }

  /**
   * Get the base url of a user image on s3.
   */
  def getBaseUrlByUserId(userId: Long): Option[String] = {
    getByUserId(userId) map { image =>
      "https://s3.amazonaws.com/%s.%s/%s".format(
        play.configuration("application.name"),
        play.configuration("application.mode"),
        image.s3Key)
    }
  }

  /**
   * Get a photo associated with a recipe by the id of the recipe.
   */
  def getByRecipeId(recipeId: Long): Option[Image] = {
    SQL("""
        select * from Image i
        join RecipeImage ri on i.id = ri.imageId
        join Recipe r on ri.recipeId = r.id
        where r.id = {recipeId}
        """)
      .on("recipeId" -> recipeId)
      .as(Image ?)
  }

  /**
   * Get the base url of a recipe image on s3.
   */
  def getBaseUrlByRecipeId(recipeId: Long): Option[String] = {
    getByRecipeId(recipeId) map { image =>
      "https://s3.amazonaws.com/%s.%s/%s".format(
        play.configuration("application.name"),
        play.configuration("application.mode"),
        image.s3Key)
    }
  }
}
