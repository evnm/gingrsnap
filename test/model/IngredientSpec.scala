import java.sql.Timestamp
import models.{Ingredient, Recipe, GingrsnapUser}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class IngredientSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = {
    Fixtures.deleteDatabase()
    GingrsnapUser.create(
      GingrsnapUser(Id(0), "bob@gmail.com", "secret", "1", "Bob", timestamp, None, None))
    Recipe.create(
      Recipe(Id(1), "Fish sticks", "fish-sticks", 0, timestamp, timestamp, None, "dems tasty"))
  }
  val timestamp = new Timestamp(System.currentTimeMillis())

  it should "create and retrieve a Ingredient" in {
    Ingredient.create(Ingredient(NotAssigned, "potato", 1, timestamp))
    val potato = Ingredient.find("name={name}").on("name" -> "potato").first()

    Ingredient.count().single() should be (1)
    potato should not be (None)
    potato.get.name should be ("potato")
  }

  it should "lookup by recipe id" in {
    Ingredient.create(Ingredient(Id(1), "potato", 1, timestamp))
    Ingredient.create(Ingredient(Id(2), "carrot", 1, timestamp))
    val ingrs = Ingredient.getByRecipeId(1)

    ingrs should not be (Seq.empty)
    ingrs(0).name should be ("potato")
    ingrs(1).name should be ("carrot")
  }
}
