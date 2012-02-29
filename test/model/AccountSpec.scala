import java.sql.Timestamp
import models.{Account, GingrsnapUser}
import play.db.anorm._
import play.test._
import org.scalatest._
import org.scalatest.matchers._

class AccountSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = {
    Fixtures.deleteDatabase()
    SQL("""
        insert into gingrsnapuser
        (id, emailAddr, password, salt, fullname, slug, createdAt) values
        (0, 'bob@gmail.com', 'secret', '1', 'Bob', 'bob', '2012-2-29')
        """).execute()
  }

  val timestamp = new Timestamp(System.currentTimeMillis())

  it should "create and retrieve a Account" in {
    // TODO: Uncouple this with GingrsnapUser creation.
    Account.create(Account(NotAssigned, 0, Some("Mountain Ranch, CA")))
    val acct = Account.find("userId={userId}").on("userId" -> 0).first()

    Account.count().single() should be (1)
    acct should not be (None)
    acct.get.userId should be (0)
    acct.get.location should be (Some("Mountain Ranch, CA"))
  }

  it should "update an account" in {
    Account.create(Account(NotAssigned, 0, Some("Mountain Ranch, CA"))) map { acct =>
      Account.update(acct.copy(
        location = Some("Stockholm, Sweden"),
        url = Some("http://foobar.com")))
    }

    val acct = Account.find("userId=0").first()

    Account.count().single() should be (1)
    acct should not be (None)
    acct.get.userId should be (0)
    acct.get.location should be (Some("Stockholm, Sweden"))
    acct.get.url should be (Some("http://foobar.com"))
  }
}
