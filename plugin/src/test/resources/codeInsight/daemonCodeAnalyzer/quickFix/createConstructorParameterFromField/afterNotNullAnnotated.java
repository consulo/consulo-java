// "Add constructor parameter" "true"
import javax.annotation.Nonnull;

class A {
  @Nonnull
  private final Object field;

  A(@Nonnull Object field, String... strs) {
      this.field = field;<caret>
  }

}