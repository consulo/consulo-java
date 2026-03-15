import org.jspecify.annotations.Nullable;

public class TestNPEafterNew {
  @Nullable
  Object[] arr;
  void test(Object[] notnull) {
      arr = notnull;
      System.out.println(arr.length);
  }

  void test() {
      arr = new Object[5];
      System.out.println(arr.length);
  }
}
