import java.util.Date
import models.Recipe
import play.jobs.{OnApplicationStart, Job}

@OnApplicationStart class BootStrap extends Job {
  override def doJob {
    import models._
    import play.test._

    // Import initial data if the database is empty.
    if (User.count().single() == 0) {
      Yaml[List[Any]]("init-data.yml") foreach {
        _ match {
          case u:  User => User.create(u)
          case p:  Account => Account.create(p)
          case r:  Recipe => Recipe.create(r)
          case i:  Ingredient => Ingredient.create(i)
        }
      }

      // SnakeYAML can't handle Option[Date]s, so hand-publish each recipe.
      val date = new Date()
      Recipe.find().list() foreach { recipe =>
        Recipe.update(recipe.copy(publishedAt = Some(date)))
      }
    }
  }
}
