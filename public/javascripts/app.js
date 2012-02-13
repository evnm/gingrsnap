var unhighlight = function(element, errorClass, validClass) {
  // Delete the span.
  var next = $(element).next();
  if (next.is("span")) {
    next.remove();
  }
}

var errorPlacement = function(error, element) {
  if (!$(element).next().is("span")) {
    element.after("<span class=\"help-inline alert-error\">" + error.text() + "</span>");
  }
}

/**
 * Signup form validation.
 */
$(document).ready(function() {
  $("#signup-form").validate({
    unhighlight: unhighlight,
    errorPlacement: errorPlacement,
    rules: {
      fullname: "required",
      emailAddr: {
        required: true,
        email: true
      },
      password: {
        required: true,
        minlength: 6
      },
      confirmpassword: {
        required: true,
        equalTo: "#password"
      }
    },
    messages: {
      emailAddr: {
        email: "Please provide a valid email address."
      },
      password: {
        minlength: jQuery.format("Password must be At least {0} characters long.")
      }
    }
  });
});

/**
 * Recipe form logic.
 */
$(document).ready(function() {
  $("textarea.wysiwyg").wysiwyg({
    iFrameClass: "iframe",
    initialContent: "<p></p>",
    autoGrow: true,
    rmUnusedControls: true,
    css: "/public/stylesheets/wysiwyg-editor.css",
    controls: {
      bold: { visible: true },
      italic: { visible: true },
      createLink: { visible: true },
      insertUnorderedList: { visible: true },
      insertOrderedList: { visible: true }
    }
  });

  // Initially focus on title and generate slug on unfocus.
  $("input#title").focus().blur(function() {
    $("input#slug").val(
      $(this).val()
        .toLowerCase()
        .replace(/ /g,'+')
        .replace(/[^\w+]+/g,'')
    )
  });

  // Translate "return" in ingredient input to add-button click.
  $("#ingredient input").keypress(function(event) {
    var code = event.keyCode || event.which;
    if (code == 13) {
      $("button#add-ingredient").click();
      event.preventDefault();
    }
  });

  // Add ingredient button click action.
  $("button#add-ingredient").click(function() {
    var ingrInput = $("#ingredient input")
    if (ingrInput.val()) {
      $("ul#ingredients-inputs")
        .append('<li><div class="input-append"><input class="span4" type="text" value="' +
                ingrInput.val().replace(/\"/g, "&#34;") + '" /><span class="add-on">' +
                '<a class="close" href="#">&times;</a></span></div>');
      ingrInput.val("").focus();
    }
  });

  // Delete ingredient button click action.
  $("ul#ingredients-inputs a.close").live("click", function() {
    $(this).parents("li").remove();
    return false;
  });

  // Fill in the "name" attributes of ingredient inputs on form submission.
  $("form#recipe-form").submit(function() {
    $("ul#ingredients-inputs")
      .find("li input")
      .each(function(i, input) {
        $(input).attr("name", "ingredients[" + i + "]");
      });
  });

  // Clicking publish button sets hidden isPublished input to true.
  $("button#publish").click(function() {
    $("input#isPublished").val(true);
  });

  // Cancel button pops up confirmation, then redirects.
  $("form#recipe-form button.cancel").click(function(event) {
    if (confirm("Cancel editing this recipe? All progress will be lost")) {
      window.location.href = "/";
    }
    event.preventDefault();
  });
});

/**
 * Recipe show page logic.
 */
$(document).ready(function() {
  // Delete recipe button.
  $("a#delete-recipe").click(function(event) {
    if (confirm("Are you sure you want to delete this recipe?")) {
      var form = document.createElement("form");
      form.style.display = "none";
      this.parentNode.appendChild(form);
      form.method = "POST";
      form.action = "/recipes/delete/" + $("span#recipe-id").text();
      form.submit();
    }
    event.preventDefault();
  });

  $("button.make-recipe")
    .click(function(event) {
      var that = $(this);
      that.button('loading');
      $.ajax({
        type: "POST",
        dataType: "text json",
        url: "/makes/new",
        data: "userId=" + $("span#user-id").text() + "&recipeId=" +
          $("span#recipe-id").text(),
        success: function(response) {
          if (response.error) {
            // TODO
          } else {
            that.button("complete");
            that.attr("disabled", "true");
            var totalMakeCount = $("span#total-make-count");
            // Add or remove 's' from "time[s]" label, if necessary.
            if (totalMakeCount.text() == "0") {
              $("span#total-make-times").text("time");
            } else if (totalMakeCount.text() == "1") {
              $("span#total-make-times").text("times");
            }
            totalMakeCount.text(parseInt(totalMakeCount.text()) + 1);

            var userMakeCount = $("span#user-make-count");
            if (userMakeCount.text() == "0") {
              $("span#user-make-times").text("time");
            } else if (userMakeCount.text() == "1") {
              $("span#user-make-times").text("times");
            }
            userMakeCount.text(parseInt(userMakeCount.text()) + 1);
          }
        }
      });
      event.preventDefault();
    });
});

/**
 * User show page logic.
 */
$(document).ready(function() {
  // Delete draft button.
  $("ul.draft-list a.close").click(function(event) {
    if (confirm("Are you sure you want to delete this draft?")) {
      var form = document.createElement("form");
      form.style.display = "none";
      this.parentNode.appendChild(form);
      form.method = "POST";
      // Get recipeId from prev span element.
      form.action = "/recipes/delete/" + $(this).prev().text();
      form.submit();
    }
    event.preventDefault();
  });

  // Follow button behavior.
  $("button.follow-user").live("click", function(event) {
    var that = $(this);
    var userInfoDiv = $(that).parent("div");
    $.ajax({
      type: "POST",
      dataType: "text json",
      url: "/follows/new/user",
      data: "userId=" + $("#user-id", userInfoDiv).text(),
      success: function(response) {
        if (response.error) {
          // TODO
        } else {
          that.toggleClass("follow-user");
          that.toggleClass("unfollow-user");
          that.toggleClass("btn-danger");
          that.text("Unfollow");
        }
      }
    });
    event.preventDefault();
  });

  // Unfollow button behavior.
  $("button.unfollow-user").live("click", function(event) {
    var that = $(this);
    var userInfoDiv = $(that).parent("div");
    $.ajax({
      type: "POST",
      dataType: "text json",
      url: "/follows/delete/user",
      data: "userId=" + $("#user-id", userInfoDiv).text(),
      success: function(response) {
        if (response.error) {
          // TODO
        } else {
          that.toggleClass("follow-user");
          that.toggleClass("unfollow-user");
          that.toggleClass("btn-danger");
          that.text("Follow");
        }
      }
    });
    event.preventDefault();
  });
});

/**
 * Account editing form.
 */
$(document).ready(function() {
  $("div.oauth-connection-badge a.close").click(function(event) {
    if (confirm("Are you sure you want to revoke the connection of your Twitter account to Gingrsnap?")) {
      var form = document.createElement("form");
      form.style.display = "none";
      this.parentNode.appendChild(form);
      form.method = "POST";
      form.action = "/oauth/revoke/twitter";
      form.submit();
    }
    event.preventDefault();
  });

  // Validation.
  $("form#edit-account").validate({
    highlight: function(element, errorClass) {
      var inputDiv = $(element).parent("div");
      inputDiv.parent("div")
        .removeClass("success")
        .addClass("error");
    },
    unhighlight: function(element, errorClass, validClass) {
      var inputDiv = $(element).parent("div");
      inputDiv.parent("div")
        .removeClass("error")
        .addClass("success");

      // Delete the span.
      inputDiv.children("span.help-inline").remove();
    },
    errorPlacement: function(error, element) {
      if ($(element).parent("div").children("span").length == 0) {
        element.after("<span class=\"help-inline\">" + error.text() + "</span>");
      }
    },
    rules: {
      newpassword: {
        minlength: 6
      },
      confirmnewpassword: {
        required: "#newPassword:filled",
        equalTo: "#newPassword"
      },
      image: {
        accept: "png|jpe?g|gif"
      }
    },
    messages: {
      newpassword: {
        minlength: jQuery.format("Password must be At least {0} characters long.")
      }
    }
  });
});

/**
 * Contact modal form ajax.
 */
$(document).ready(function() {
  $("form#feedback-form").submit(function(event) {
    var that = $(this).find("button");
    that.button('loading');
    event.preventDefault();
    $.post(
      "/feedback",
      { feedbackBody: $(this).find("textarea").val() },
      function(response) {
        if (response.error) {
          // TODO
        } else {
          that.button("complete");
          that.attr("disabled", "true");
        }
      }
    );
  });
});

/**
 * Event feeds.
 */
$(document).ready(function() {
  // jQuery timeago.
  $(".timeago").each(function(i, el) {
    $(el).timeago();
  });

  // Pagination.
  $("ol.event-feed li#pagination-control button").click(function(event) {
    var lastLi = $(this).parent();
    var ol = lastLi.parent("ol")
    // TODO: Couldn't get GET requests to work here. Issue with Play?
    $.ajax({
      type: "POST",
      dataType: "text json",
      url: "/events/getNextPage",
      data: {
        eventFeedType: $("#event-feed-type", ol).text(),
        lastTimestamp: $("#event-timestamp", lastLi.prev()).text(),
        userId: $("#user-id", ol).text(),
        n: $("#page-size", ol).text()
      },
      success: function(response) {
        if (response.error) {
          // TODO
        } else {
          $.each(response.events, function(i, event) {
            var result = '<li>';
            if (event.eventType == 0) {
              result += '<i class="icon-list-alt"></i><a href="/' +
                event.subjectSlug + '">' + event.subjectFullname +
                '</a> published recipe <a href="/' + event.authorSlug + '/' +
                event.recipeSlug + '">' + event.recipeTitle + '</a>';
            } else if (event.eventType == 1) {
              result += '<a href="/' + event.subjectSlug + '">' +
                event.subjectFullname + '</a> forked recipe <a href="/' +
                event.authorSlug + '/' + event.recipeSlug + '">' +
                event.recipeTitle + '</a>';
            } else if (event.eventType == 2) {
              result += '<i class="icon-pencil"></i><a href="/' +
                event.subjectSlug + '">' + event.subjectFullname +
                '</a> updated recipe <a href="/' + event.authorSlug + '/' +
                event.recipeSlug + '">' + event.recipeTitle + '</a>';
            } else if (event.eventType == 3) {
              result += '<i class="icon-ok"></i><a href="/' + event.subjectSlug +
                '">' + event.subjectFullname + '</a> made <a href="/' +
                event.authorSlug + '/' + event.recipeSlug + '">' +
                event.recipeTitle + '</a>';
            } else if (event.eventType == 4) {
              result += '<i class="icon-comment"></i><a href="/' +
                event.subjectSlug + '">' + event.subjectFullname +
                '</a> left a tip on <a href="/' + event.authorSlug + '/' +
                event.recipeSlug + '">' + event.recipeTitle + '</a>';
            } else if (event.eventType == 5) {
              result += '<i class="icon-user"></i><a href="/' +
                event.subjectSlug + '">' + event.subjectFullname +
                '</a> started following <a href="/' + event.objSlug +
                '">' + event.objFullname + '</a>';
            }


            result += ' <span class="timeago">' +
              $.timeago(event.createdAt) + '</span>' +
              '<span id="event-timestamp" style="display: none;">' +
              event.createdAt + '</span></li>';
            $(lastLi).before(result)
          });
        }
      }
    });
    event.preventDefault();
  });
});
