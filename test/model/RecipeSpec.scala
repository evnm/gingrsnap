import java.sql.Timestamp
import models.{Ingredient, Recipe, GingrsnapUser}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class RecipeSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = {
    Fixtures.deleteDatabase()
    GingrsnapUser.create(
      GingrsnapUser(Id(0), "bob@gmail.com", "secret", "1", "Bob", "bob", timestamp, None, None))
  }

  val timestamp = new Timestamp(System.currentTimeMillis())

  it should "create and retrieve a Recipe" in {
    // TODO: Uncouple this with GingrsnapUser creation.
    Recipe.create(Recipe(NotAssigned, "foo pie", "foo-pie", 0, timestamp, timestamp, None, "junk"))
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

  it should "lookup by recipe id" in {
    Recipe.create(Recipe(Id(1), "Fish sticks", "fish-sticks", 0, timestamp, timestamp, None, "dems tasty"))
    val recipe = Recipe.getById(1)

    recipe should not be (None)
    recipe map { r =>
      r.title should be ("Fish sticks")
      r.slug should be ("fish-sticks")
      r.authorId should be (0)
      r.body should be ("dems tasty")
    }
  }

  it should "lookup by user id" in {
    Recipe.create(Recipe(Id(1), "Fish sticks", "fish-sticks", 0, timestamp, timestamp, None, "dems tasty"))
    Recipe.create(Recipe(Id(2), "Cow pies", "cow-pies", 0, timestamp, timestamp, None, "Buy a cow."))
    val recipes = Recipe.getAllByUserId(0)

    recipes should not be (Seq.empty)
    recipes(0).title should be ("Fish sticks")
    recipes(0).slug should be ("fish-sticks")
    recipes(0).body should be ("dems tasty")
    recipes(1).title should be ("Cow pies")
    recipes(1).slug should be ("cow-pies")
    recipes(1).body should be ("Buy a cow.")
  }

  it should "lookup by slugs" in {
    Recipe.create(Recipe(Id(1), "Fish sticks", "fish-sticks", 0, timestamp, timestamp, None, "dems tasty"))
    Ingredient.create(Ingredient(Id(1), "potato", 1, timestamp))
    Ingredient.create(Ingredient(Id(2), "carrot", 1, timestamp))
    val recipe = Recipe.getBySlugs("bob", "fish-sticks")

    recipe should not be (None)
    recipe map { case (r, ingrs) =>
      r.title should be ("Fish sticks")
      r.slug should be ("fish-sticks")
      r.authorId should be (0)
      r.body should be ("dems tasty")
      ingrs.length should be (2)
    }
  }
}
