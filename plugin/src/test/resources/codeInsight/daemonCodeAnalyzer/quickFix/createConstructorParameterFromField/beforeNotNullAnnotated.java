// "Add constructor parameter" "true"
import javax.annotation.Nonnull;

class A {
  @Nonnull
  private final Object <caret>field;

  A(String... strs) {
  }

}