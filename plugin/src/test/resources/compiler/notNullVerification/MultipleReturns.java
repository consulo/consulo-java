import jakarta.annotation.Nonnull;

public class MultipleReturns {
  @Nonnull
  public Object test(int i) {
    if (i == 0) return null;
    if (i == 1) return null;
    return null;
  }
}