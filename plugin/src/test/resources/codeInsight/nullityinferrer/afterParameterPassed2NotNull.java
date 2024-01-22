import jakarta.annotation.Nonnull;

class Test {
  void foo(@Nonnull String s) {
  }

  void bar(@Nonnull String str) {
    foo(str);
  }
}