import java.util.Date
import models.User
import org.scalatest._
import org.scalatest.matchers._
import play.db.anorm._
import play.test._
import twitter4j.auth.AccessToken

// TODO: Compare against user itself once date issue is figured out.
class UserSpec extends UnitFlatSpec with ShouldMatchers with BeforeAndAfterEach {
  override def beforeEach() = Fixtures.deleteDatabase()
  val date = new Date(System.currentTimeMillis)

  it should "create and retrieve a User" in {
    User.create(User(NotAssigned, "bob@gmail.com", "secret", "1", "Bob", date, None, None))
    val user = User.find("emailAddr={email}").on("email" -> "bob@gmail.com").first()

    User.count().single() should be (1)
    user should not be (None)
    user map { u =>
      u.password should be ("secret")
      u.salt should be ("1")
      u.fullname should be ("Bob")
      u.emailAddr should be ("bob@gmail.com")
    }
  }

  it should "lookup by email" in {
    User.create(User(NotAssigned, "bob@gmail.com", "secret", "1", "Bob", date, None, None))
    val user = User.getByEmail("bob@gmail.com")

    user should not be (None)
    user map { u =>
      u.password should be ("secret")
      u.salt should be ("1")
      u.fullname should be ("Bob")
      u.emailAddr should be ("bob@gmail.com")
    }
  }

  it should "lookup by email and password" in {
    User.create(User("bob@gmail.com", "secret", "Bob", None, None))
    val user = User.getByEmailAndPass("bob@gmail.com", "secret")

    user should not be (None)
    user map { u =>
      u.fullname should be ("Bob")
      u.emailAddr should be ("bob@gmail.com")
    }
  }

  it should "lookup by Twitter access token" in {
    val token = new AccessToken("0-twtoken", "twtokensecret")
    User.create(
      User(NotAssigned, "bob@gmail.com", "secret", "1", "Bob", date,
           Some(token.getToken), Some(token.getTokenSecret)))
    val user = User.getByTwAuth(token)

    user should not be (None)
    user map { u =>
      u.password should be ("secret")
      u.salt should be ("1")
      u.fullname should be ("Bob")
      u.emailAddr should be ("bob@gmail.com")
      u.twAccessToken should be (Some(token.getToken))
      u.twAccessTokenSecret should be (Some(token.getTokenSecret))
    }
  }
}
