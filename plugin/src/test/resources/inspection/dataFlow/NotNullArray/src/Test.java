import javax.annotation.Nonnull;

public class TestNPEafterNew {
  @javax.annotation.Nullable
  Object[] arr;
  void test(@Nonnull Object[] notnull) {
      arr = notnull;
      System.out.println(arr.length);
  }

  void test() {
      arr = new Object[5];
      System.out.println(arr.length);
  }
}
