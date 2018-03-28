import javax.annotation.Nonnull;

class Test {
  @Nonnull
  Object foo() {
    Object res;
    res = null;
    return res;
  }
}