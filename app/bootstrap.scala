import java.sql.Timestamp
import models.Recipe
import play.jobs.{OnApplicationStart, Job}

@OnApplicationStart class BootStrap extends Job {
  override def doJob {
    import models._
    import play.test._

    // Import initial data if the database is empty.
    if (GingrsnapUser.count().single() == 0) {
      Yaml[List[Any]]("init-data.yml") foreach { blob =>
        println("blob: " + blob)
        blob match {
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
  }
}
