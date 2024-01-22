import jakarta.annotation.Nonnull;

public class StaticInnerClass {
  public StaticInnerClass() {
    new Inner("");
  }

  public static class Inner {
    public Inner(@Nonnull String s) {
    }
  }
}
