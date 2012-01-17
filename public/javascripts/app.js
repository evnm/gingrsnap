var highlight = function(element, errorClass) {
  var inputDiv = $(element).parent("div");
  inputDiv.parent("div")
    .removeClass("success")
    .addClass("error");
}
var unhighlight = function(element, errorClass, validClass) {
  var inputDiv = $(element).parent("div");
  inputDiv.parent("div")
    .removeClass("error")
    .addClass("success");

  // Delete the span.
  inputDiv.children("span").remove();
}
var errorPlacement = function(error, element) {
  if ($(element).parent("div").children("span").length == 0) {
    element.after("<span class=\"help-inline\">" + error.text() + "</span>");
  }
}

/**
 * Signup form validation.
 */
$(document).ready(function() {
  $("#signup-form").validate({
    highlight: highlight,
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
  $("input#ingredient").keypress(function(event) {
    var code = event.keyCode || event.which;
    if (code == 13) {
      $("button#add-ingredient").click();
      event.preventDefault();
    }
  });

  // Add ingredient button click action.
  $("button#add-ingredient").click(function() {
    var ingrInput = $("input#ingredient")
    if (ingrInput.val()) {
      $("ul#ingredients")
        .append('<li><div class="input"><div class="input-append">' +
                '<input class="span4" type="text" value="' +
                ingrInput.val().replace(/\"/g, "&#34;") + '" /><label class="add-on">' +
                '<a class="close" href="#">x</a></label></div></div>' +
                '</li><li class="clearfix"></li>');
      ingrInput.val("").focus();
    }
  });

  // Delete ingredient button click action.
  $("ul#ingredients a.close").live("click", function() {
    var li = $(this).parents("li");
    li.next().remove();
    li.remove();
    return false;
  });

  // Fill in the "name" attributes of ingredient inputs on form submission.
  $("form#recipe-form").submit(function() {
    $("ul#ingredients")
      .find("li:not(.clearfix) input")
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
            totalMakeCount.text(parseInt(totalMakeCount.text()) + 1);
            var userMakeCount = $("span#user-make-count");
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
