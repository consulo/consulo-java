// "Remove annotation" "true"

import javax.annotation.Nonnull;

class Foo {
  <caret>@Nonnull
  int foo(){return 0;}
}