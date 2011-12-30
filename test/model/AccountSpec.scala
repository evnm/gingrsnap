import models.{Account, GingrsnapUser}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class AccountSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = Fixtures.deleteDatabase()

  it should "create and retrieve a Account" in {
    // TODO: Uncouple this with GingrsnapUser creation.
    GingrsnapUser.create(GingrsnapUser(Id(0), "bob@gmail.com", "secret", "1", "Bob", new java.util.Date, None, None))
    Account.create(Account(NotAssigned, 0, Some("Mountain Ranch, CA")))
    val acct = Account.find("userId={userId}").on("userId" -> 0).first()

    Account.count().single() should be (1)
    acct should not be (None)
    acct.get.userId should be (0)
    acct.get.location should be (Some("Mountain Ranch, CA"))
  }
}
