package notifiers;

import models.GingrsnapUser;
import play.mvc.Mailer;

public class Mails extends Mailer {
  /**
   * NOTE: welcome is a noop if user has no emailAddr set.
   */
  public static void welcome(GingrsnapUser user) {
    if (user.emailAddr().isDefined()) {
      setSubject("Welcome to Gingrsnap!");
      addRecipient(user.emailAddr().get());
      setFrom("Gingrsnap <team@gingrsnap.com>");
      send(user);
    }
  }

  public static void feedback(String feedbackBody, GingrsnapUser user) {
    setSubject("Feedback from " + user.fullname());
    addRecipient("evan@gingrsnap.com");

    if (user.emailAddr().isDefined()) {
      setFrom("'" + user.fullname() + "' <" + user.emailAddr().get() + ">");
    } else {
      setFrom("'" + user.fullname() + "' <feedback@gingrsnap.com>");
    }

    send(feedbackBody);
  }

  public static void feedback(String feedbackBody) {
    setSubject("Anonymous Feedback");
    addRecipient("evan@gingrsnap.com");
    setFrom("Feedback <feedback@gingrsnap.com>");
    send(feedbackBody);
  }

  public static void resetPassword(String emailAddr, String fullname, String confirmationUrl) {
    setSubject("Reset your Gingrsnap password");
    addRecipient(emailAddr);
    setFrom("Gingrsnap <noreply@gingrsnap.com>");
    send(fullname, confirmationUrl);
  }
}
