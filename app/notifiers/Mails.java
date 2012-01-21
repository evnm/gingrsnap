package notifiers;

import models.GingrsnapUser;
import play.mvc.Mailer;

public class Mails extends Mailer {
  public static void welcome(GingrsnapUser user) {
    setSubject("Welcome to Gingrsnap!");
    addRecipient(user.emailAddr());
    setFrom("Gingrsnap <team@gingrsnap.com>");
    send(user);
  }
}
