
// "Add constructor parameter" "true"
class A {
  private final Object field;

  A(Object field, String... strs) {
      this.field = field;<caret>
  }

}