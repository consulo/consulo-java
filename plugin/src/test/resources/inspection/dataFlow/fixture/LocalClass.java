import org.jspecify.annotations.Nullable;

class Test {
  void f(@Nullable final Object x) {
    if (x != null) {
      class C {
        C(Object x) {
        }

        C() {
          this(x);
        }
      }
    }
  }
}