import java.sql.Timestamp
import models._
import play.db.anorm._
import play.jobs.{OnApplicationStart, Job}
import play.Logger
import play.test._

@OnApplicationStart class Bootstrap extends Job {
  override def doJob {
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
                EventType.RecipeCreation.id,
                subjectId = recipe.authorId,
                objectId = recipe.id(),
                createdAt = recipe.createdAt))
          }
        }
      }
    }
  }
}
