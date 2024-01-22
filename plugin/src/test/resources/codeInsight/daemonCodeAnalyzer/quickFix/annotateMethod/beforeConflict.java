// "Remove annotation" "true"

import jakarta.annotation.Nullable;

class Foo {
  <caret>@jakarta.annotation.Nonnull @Nullable
  String foo(){return "";}
}