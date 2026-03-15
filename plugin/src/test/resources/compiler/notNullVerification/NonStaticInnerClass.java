
public class NonStaticInnerClass {
  public NonStaticInnerClass() {
    new Inner("");
  }

  public class Inner {
    public Inner(String s) {
    }
  }
}
