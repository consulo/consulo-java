// "Remove annotation" "true"

import org.jspecify.annotations.Nullable;

class Foo {
  <caret>@jakarta.annotation.Nonnull @Nullable
  String foo(){return "";}
}