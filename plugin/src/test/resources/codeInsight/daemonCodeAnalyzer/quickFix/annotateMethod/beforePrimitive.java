// "Remove annotation" "true"

import jakarta.annotation.Nonnull;

class Foo {
  <caret>@Nonnull
  int foo(){return 0;}
}