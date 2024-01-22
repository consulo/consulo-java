import jakarta.annotation.Nonnull;

// "Add constructor parameter" "true"
class A {
  @Nonnull
  private final Object <caret>field;

  A(String... strs) {
  }

}