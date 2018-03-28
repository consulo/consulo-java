// "Remove annotation" "true"

import javax.annotation.*;

class Foo {
  <caret>@Nonnull @javax.annotation.Nullable
  String foo(){return "";}
}