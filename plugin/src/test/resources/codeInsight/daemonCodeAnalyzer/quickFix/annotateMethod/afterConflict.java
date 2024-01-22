// "Remove annotation" "true"

import jakarta.annotation.Nullable;

class Foo {
  <caret>@Nullable
  String foo(){return "";}
}