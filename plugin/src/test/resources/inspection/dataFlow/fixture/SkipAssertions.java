
class Test {
  private static void test(Object foo) {
    assert foo != null;
  }

  private static void test2(Object foo) {
    if (foo == null) {
      throw new IllegalArgumentException();
    }
  }
  private static void test3(@jakarta.annotation.Nonnull Object foo) {
    if (foo == null) throw new IllegalArgumentException();
  }

  private static void test4(Object foo) {
    if (<warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>) throw new IllegalArgumentException();
  }

}