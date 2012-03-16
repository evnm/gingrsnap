import com.amazonaws.services.s3.model.{
  CannedAccessControlList, DeleteObjectRequest, PutObjectRequest}
import java.io.{File, FileOutputStream}
import java.sql.Timestamp
import models._
import org.apache.commons.net.io
import play.{Logger, Play}
import play.cache.Cache
import play.db.anorm._
import play.jobs.{OnApplicationStart, Job}
import play.libs.Images
import play.test._
import s3.S3
import twitter4j.auth.AccessToken

@OnApplicationStart class Bootstrap extends Job {
  override def doJob {
    if (Play.mode.isDev) {
      // Import initialization data if the database is empty.
      if (GingrsnapUser.count().single() == 0) {
        Logger.info("Bootstrap task: Importing initialization data into database")
        Yaml[List[Any]]("init-data.yml") foreach {
          _ match {
            case u:  GingrsnapUser => GingrsnapUser.create(u)
            case p:  Account => Account.create(p)
            case r:  Recipe => Recipe.create(r)
            case i:  Ingredient => Ingredient.create(i)
          }
        }

        // SnakeYAML can't handle Option[Timestamp]s, so hand-publish each recipe.
        val timestamp = new Timestamp(System.currentTimeMillis())
        Recipe.find().list() foreach { recipe =>
          Recipe.update(recipe.copy(publishedAt = Some(timestamp)))
        }
      }

      // Backfill events.
      if (Event.count().single() == 0) {
        Logger.info("Bootstrap task: Backfilling events")
        Recipe.find().list() foreach { recipe =>
          if (recipe.publishedAt.isDefined) {
            if (recipe.parentRecipe.isDefined) {
              Event.create(
                Event(
                  NotAssigned,
                  EventType.RecipeFork.id,
                  subjectId = recipe.authorId,
                  objectId = recipe.id(),
                  createdAt = recipe.publishedAt.get))
            } else {
              Event.create(
                Event(
                  NotAssigned,
                  EventType.RecipePublish.id,
                  subjectId = recipe.authorId,
                  objectId = recipe.id(),
                  createdAt = recipe.createdAt))
            }
          }
        }
      }
    }

    /**
     * S3 image backfill.

    Logger.info("Bootstrap task: Backfilling new image sizes on S3")
    val originalFile = File.createTempFile("original", null)
    val tempFile = File.createTempFile("temp", null)

    Image.find().list() foreach { image =>
      // Get original file from S3.
      val s3InStream = S3.client.getObject(S3.bucket, image.s3Key + "_original." + image.extension)
        .getObjectContent()
      val fileOutStream = new FileOutputStream(originalFile)
      io.Util.copyStream(s3InStream, fileOutStream)
      s3InStream.close()
      fileOutStream.close()

      S3.client.deleteObject(S3.bucket, image.s3Key + "_thumbnail." + image.extension)

      // Generate and upload new cropped/resized versions.
      Seq("thumbnail") foreach { sizeKey =>
        Image.SizeMap(sizeKey) match {
          case (Some(x), Some(y)) if x == y => Image.cropSquare(originalFile, tempFile, x)
          case (Some(x), Some(y)) => Images.resize(originalFile, tempFile, x, y)
          case (Some(x), None) => Images.resize(originalFile, tempFile, x, -1)
          case (None, Some(y)) => Images.resize(originalFile, tempFile, -1, y)
          case invalid => Logger.error("Invalid Image.SizeMap entry: %s".format(invalid))
        }
        S3.client.putObject(
          new PutObjectRequest(S3.bucket, image.s3Key + "_" + sizeKey + "." + image.extension, tempFile)
            .withCannedAcl(CannedAccessControlList.PublicRead))
      }
    }

    originalFile.delete()
    tempFile.delete()
*/
    /**
     * Twitter user id backfill.

    Logger.info("Bootstrap task: Backfilling Twitter user ids")
    GingrsnapUser.find().list() filter { user =>
      user.twAccessToken.nonEmpty && user.twAccessTokenSecret.nonEmpty
    } foreach { user =>
      GingrsnapUser.update(
        user.copy(twUserId = Some(
          new AccessToken(user.twAccessToken.get, user.twAccessTokenSecret.get)
            .getUserId())))
    }
    */

    // Reset all cached feature flags.
    Feature.find().list() foreach { feature =>
      Cache.set(Feature.cacheKey(feature.id()), feature.state, "24h")
    }
  }
}
