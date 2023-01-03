import javax.annotation.Nonnull;

class Test {
  void foo(@Nonnull String s) {
  }

  void bar(String str) {
    foo(str);
  }
}