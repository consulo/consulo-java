import jakarta.annotation.Nonnull;

public class ASD {
  static void foo(Object any) {
    boolean a = false;
    boolean b = false;
    while (true) {
      boolean trg = bar() == any;

      a = a || trg;
      b = b || !trg;

      if (b && a) break;
    }
  }

  @Nonnull
  static Object bar() {
    return new Object();
  }
}
