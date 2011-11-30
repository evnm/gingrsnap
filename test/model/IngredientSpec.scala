import java.util.Date
import models.Ingredient
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class IngredientSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = Fixtures.deleteDatabase()
  val date = new Date(System.currentTimeMillis)

  it should "create and retrieve a Ingredient" in {
    Ingredient.create(Ingredient(NotAssigned, "potato", date))
    val potato = Ingredient.find("name={name}").on("name" -> "potato").first()

    Ingredient.count().single() should be (1)
    potato should not be (None)
    potato.get.name should be ("potato")
  }
}
