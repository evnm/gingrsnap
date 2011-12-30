import java.util.Date
import models.{Ingredient, Recipe, GingrsnapUser}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class IngredientSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = {
    Fixtures.deleteDatabase()
    GingrsnapUser.create(GingrsnapUser(Id(0), "bob@gmail.com", "secret", "1", "Bob", date, None, None))
    Recipe.create(Recipe(Id(1), "Fish sticks", "fish-sticks", 0, date, date, "dems tasty"))
  }
  val date = new Date(System.currentTimeMillis)

  it should "create and retrieve a Ingredient" in {
    Ingredient.create(Ingredient(NotAssigned, "potato", 1, date))
    val potato = Ingredient.find("name={name}").on("name" -> "potato").first()

    Ingredient.count().single() should be (1)
    potato should not be (None)
    potato.get.name should be ("potato")
  }

  it should "lookup by recipe id" in {
    Ingredient.create(Ingredient(Id(1), "potato", 1, date))
    Ingredient.create(Ingredient(Id(2), "carrot", 1, date))
    val ingrs = Ingredient.getByRecipeId(1)

    ingrs should not be (Seq.empty)
    ingrs(0).name should be ("potato")
    ingrs(1).name should be ("carrot")
  }
}
