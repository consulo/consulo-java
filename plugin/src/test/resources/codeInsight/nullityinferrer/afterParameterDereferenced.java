import jakarta.annotation.Nonnull;

class Test {
  void foo(@Nonnull String s) {
    s.substring(0);
  }

  /**
   * @param str
   */
  void bar(@jakarta.annotation.Nonnull String str) {
    if (str.substring(0) == null) {
    }
  }

  /**
   * @param str
   */
  void bar(@jakarta.annotation.Nonnull String str) {
    if ((str).substring(0) == null) {
    }
  }
}