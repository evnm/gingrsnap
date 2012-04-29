package models

import com.amazonaws.services.s3.model.{
  CannedAccessControlList, DeleteObjectRequest, ObjectMetadata, PutObjectRequest}
import java.awt.{Color, Image => AwtImage}
import java.awt.image.BufferedImage
import java.io.File
import java.net.URLConnection
import java.util.UUID
import javax.imageio.{IIOImage, ImageIO}
import javax.imageio.stream.FileImageOutputStream;
import play.Logger
import play.db.anorm._
import play.db.anorm.defaults._
import play.db.anorm.SqlParser._
import play.libs.Images
import play.utils.Scala.MayErr
import s3.S3
import scala.collection.JavaConversions._

case class Image(
  id: Pk[Long],
  s3Key: String,
  extension: String
)

object Image extends Magic[Image] {
  /**
   * Maximum size of uploaded images, in kilobytes.
   */
  val MaxAllowedSize = 500

  val SizeMap = Map[String, (Option[Int], Option[Int])](
    "thumbnail" -> (Some(48), Some(48)),
    "portrait" -> (Some(296), None))

  protected[this] def mimetypeToExtension(mimetype: String) = mimetype match {
    case "image/jpeg" => "jpg"
    case "image/gif" => "gif"
    case "image/png" => "png"
  }

  /**
   * Crop and resize an image to be a square of variable height/width.
   */
  def cropSquare(originalImage: File, to: File, size: Int) = {
    try {
      val source: BufferedImage = ImageIO.read(originalImage)
      val mimeType = to.getName() match {
        case filename if filename.endsWith(".png") => "image/png"
        case filename if filename.endsWith(".gif") => "image/gif"
        case _ => "image/jpeg"
      }

      val (srcHeight, srcWidth) = (source.getHeight(), source.getWidth())
      val croppedImage =
        if (srcHeight > srcWidth) {
          source.getSubimage(0, (srcHeight - srcWidth) / 2, srcWidth, srcWidth)
        } else {
          source.getSubimage((srcWidth - srcHeight) / 2, 0, srcHeight, srcHeight)
        }
      val resizedImage = croppedImage.getScaledInstance(size, size, AwtImage.SCALE_SMOOTH)
      val dest = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
      val graphics = dest.getGraphics()

      graphics.setColor(Color.WHITE)
      graphics.fillRect(0, 0, size, size)
      graphics.drawImage(resizedImage, 0, 0, null)

      val writer = ImageIO.getImageWritersByMIMEType(mimeType).next()
      val params = writer.getDefaultWriteParam()

      writer.setOutput(new FileImageOutputStream(to))
      val image = new IIOImage(dest, null, null)
      writer.write(null, image, params)
    } catch {
      case e: Throwable => {
        Logger.error("Exception thrown while attempting to crop a square image: %s".format(e.getMessage))
      }
    }
  }

  def create(file: File) = {
    val imgPathPrefix = "image/" + UUID.randomUUID().toString()
    val mimetype = URLConnection.guessContentTypeFromName(file.getName())
    val extension = mimetypeToExtension(mimetype)
    val s3ObjectMetadata = new ObjectMetadata
    s3ObjectMetadata.setContentType(mimetype)

    // First, upload the original file.
    val tempFile = File.createTempFile("original", null)
    S3.client.putObject(
      new PutObjectRequest(S3.bucket, imgPathPrefix + "_original." + extension, file)
        .withCannedAcl(CannedAccessControlList.PublicRead)
        .withMetadata(s3ObjectMetadata))

    // Then generate and upload all cropped/resized versions.
    Image.SizeMap.keySet foreach { sizeKey =>
      Image.SizeMap(sizeKey) match {
        case (Some(x), Some(y)) if x == y => cropSquare(file, tempFile, x)
        case (Some(x), Some(y)) => Images.resize(file, tempFile, x, y)
        case (Some(x), None) => Images.resize(file, tempFile, x, -1)
        case (None, Some(y)) => Images.resize(file, tempFile, -1, y)
        case invalid => Logger.error("Invalid Image.SizeMap entry: %s".format(invalid))
      }

      S3.client.putObject(
        new PutObjectRequest(S3.bucket, imgPathPrefix + "_" + sizeKey + "." + extension, tempFile)
          .withCannedAcl(CannedAccessControlList.PublicRead)
          .withMetadata(s3ObjectMetadata))
    }
    tempFile.delete()

    // Store Image object in db.
    super.create(Image(NotAssigned, imgPathPrefix, extension))
  }

  /**
   * Deletes an image by key in S3.
   */
  def delete(image: Image): Boolean = {
    Image.SizeMap.keySet + "original" foreach { sizeKey =>
      S3.client.deleteObject(S3.bucket, image.s3Key + "_" + sizeKey + "." + image.extension)
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
   * Optionally returns a tuple of (user profile image base url, image mimetype).
   */
  def getBaseUrlByUserId(userId: Long): Option[(String, String)] = {
    getByUserId(userId) map { image =>
      ("https://s3.amazonaws.com/%s.%s/%s".format(
        play.configuration("application.name"),
        play.configuration("application.mode"),
        image.s3Key), image.extension)
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
   * Optionally returns a tuple of (recipe image base url, image mimetype).
   */
  def getBaseUrlByRecipeId(recipeId: Long): Option[(String, String)] = {
    getByRecipeId(recipeId) map { image =>
      ("https://s3.amazonaws.com/%s.%s/%s".format(
        play.configuration("application.name"),
        play.configuration("application.mode"),
        image.s3Key), image.extension)
    }
  }
}
