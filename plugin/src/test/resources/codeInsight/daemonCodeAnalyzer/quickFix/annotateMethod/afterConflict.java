// "Remove annotation" "true"

import org.jspecify.annotations.Nullable;

class Foo {
  <caret>@Nullable
  String foo(){return "";}
}