import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BrokenAlignment {

  public void main(String... args) {
    @Nullable Class object = Object.class;
    output2(object);
  }

  public static void output2(@Nonnull Object value) {
    System.out.println(value);
  }
}