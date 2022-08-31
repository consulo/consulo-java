import javax.annotation.Nonnull;

class Test {
  private static void test(@Nonnull Object foo) {
    assert foo != null;
  }

  private static void test2(@Nonnull Object foo) {
    if (foo == null) {
      throw new IllegalArgumentException();
    }
  }
  private static void test3(@Nonnull Object foo) {
    if (foo == null) throw new IllegalArgumentException();
  }

  private static void test4(@Nonnull Object foo) {
    if (<warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>) throw new IllegalArgumentException();
  }

}