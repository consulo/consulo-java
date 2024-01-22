import jakarta.annotation.Nullable;

class Fun {
  @Nullable
  private Object foo;

  public Fun() {
    foo = new Object();
    makeMagic();

    if (null == foo) {
      System.out.println("hello");
    }

  }

  private void makeMagic() {
    foo = null;
  }

}