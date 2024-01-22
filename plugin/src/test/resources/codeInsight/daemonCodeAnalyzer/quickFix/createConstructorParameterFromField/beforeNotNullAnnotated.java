// "Add constructor parameter" "true"
import jakarta.annotation.Nonnull;

class A {
  @Nonnull
  private final Object <caret>field;

  A(String... strs) {
  }

}