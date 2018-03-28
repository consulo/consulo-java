import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class Test {
  void f(@Nullable final Object x) {
    if (x != null) {
      class C {
        C(@Nonnull Object x) {
        }

        C() {
          this(x);
        }
      }
    }
  }
}