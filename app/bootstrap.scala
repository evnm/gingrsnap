import play.jobs.{OnApplicationStart, Job}

@OnApplicationStart class BootStrap extends Job {
  override def doJob {
    import models.{Ingredient, Account, Recipe, User}
    import play.test._

    // Import initial data if the database is empty.
    if (User.count().single() == 0) {
      Yaml[List[Any]]("init-data.yml") foreach {
        _ match {
          case u: User => User.create(u)
          case p: Account => Account.create(p)
          case r: Recipe => Recipe.create(r)
          case i: Ingredient => Ingredient.create(i)
        }
      }
    }
  }
}
