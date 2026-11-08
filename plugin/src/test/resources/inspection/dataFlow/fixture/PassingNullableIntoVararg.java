import javax.annotation.Nonnull;

class Test {
  public static void test(@Nonnull Object... objects) { }

  public static void main(String[] args) {
    Object o = null;
    test(o);
  }
}