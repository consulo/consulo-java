public class BrokenAlignment {

  void test(@javax.annotation.Nullable Object n) {
    synchronized (<warning descr="Dereference of 'n' may produce 'java.lang.NullPointerException'">n</warning>) {

    }
  }
}