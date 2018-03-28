import javax.annotation.Nonnull;

public class Test {
  private static void foo(@Nonnull String smth) {
  }

  public static void main(String[] args) {
    String s = args[0];
    foo(null);
  }
}