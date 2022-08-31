import javax.annotation.Nonnull;

class Test {
  @Nonnull
  public String noNull(@Nonnull String text) {
    assert text != null;
    return "";
  }

  private void foo() {
    @Nonnull String str = "";
    assert str != null;
  }
}