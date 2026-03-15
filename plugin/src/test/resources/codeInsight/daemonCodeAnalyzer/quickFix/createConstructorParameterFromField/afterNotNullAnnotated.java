// "Add constructor parameter" "true"


class A {
  @jakarta.annotation.Nonnull
  private final Object field;

  A(Object field, String... strs) {
      this.field = field;<caret>
  }

}