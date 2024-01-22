import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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