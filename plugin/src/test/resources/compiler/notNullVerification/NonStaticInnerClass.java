import jakarta.annotation.Nonnull;

public class NonStaticInnerClass {
  public NonStaticInnerClass() {
    new Inner("");
  }

  public class Inner {
    public Inner(@Nonnull String s) {
    }
  }
}
