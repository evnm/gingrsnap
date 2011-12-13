import java.util.Date
import models.{Recipe, User}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class RecipeSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = Fixtures.deleteDatabase()
  val date = new Date(System.currentTimeMillis)

  it should "create and retrieve a Recipe" in {
    // TODO: Uncouple this with User creation.
    User.create(User(Id(0), "bob@gmail.com", "secret", "1", "Bob", new java.util.Date, None, None))
    Recipe.create(Recipe(NotAssigned, "foo pie", 0, date, "foo-pie", "junk"))
    val recipes = Recipe.find("authorId={id}").on("id" -> 0).as(Recipe*)
    val firstRecipe = recipes.headOption.get

    Recipe.count().single() should be (1)
    recipes.length should be (1)
    firstRecipe should not be (None)
    firstRecipe.title should be ("foo pie")
    firstRecipe.authorId should be (0)
    firstRecipe.slug should be ("foo-pie")
    firstRecipe.body should be ("junk")
  }
}
