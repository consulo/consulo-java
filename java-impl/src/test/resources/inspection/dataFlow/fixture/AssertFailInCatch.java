import javax.annotation.Nonnull;

import org.junit.Assert;

class Test {

  public static void foo() {
    String result = null;
    try {
      result = createString();
    }
    catch (Exception e) {
      Assert.fail();
    }
    finally {
      if (result == null) {
        System.out.println("Analysis failed!");
      }
    }
  }

  private static @Nonnull
  String createString() {
    throw new NullPointerException();
  }
}