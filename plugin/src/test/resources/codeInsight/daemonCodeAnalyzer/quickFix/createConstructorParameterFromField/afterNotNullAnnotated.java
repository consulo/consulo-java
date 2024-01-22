// "Add constructor parameter" "true"

import jakarta.annotation.Nonnull;

class A {
  @jakarta.annotation.Nonnull
  private final Object field;

  A(@Nonnull Object field, String... strs) {
      this.field = field;<caret>
  }

}