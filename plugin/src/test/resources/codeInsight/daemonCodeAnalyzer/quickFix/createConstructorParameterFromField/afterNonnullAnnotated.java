import jakarta.annotation.Nonnull;

// "Add constructor parameter" "true"
class A {
  @Nonnull
  private final Object field;

  A(@Nonnull Object field, String... strs) {
      this.field = field;<caret>
  }

}