public class Test {
  public @javax.annotation.Nullable
  String foo;
  
  public void test() {
    
    if (foo != null && foo.length() > 1) {
      System.out.println(foo.length());
    }
  }
}
