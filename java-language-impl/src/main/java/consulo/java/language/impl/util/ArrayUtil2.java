package consulo.java.language.impl.util;

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;

/**
 * @author VISTALL
 * @since 23-May-22
 */
public class ArrayUtil2 {
  @Contract(pure = true)
  public static int max(@jakarta.annotation.Nonnull int[] values) {
    int max = Integer.MIN_VALUE;
    for (int value : values) {
      if (value > max)
        max = value;
    }
    return max;
  }

  @Contract(pure = true)
  public static double max(@Nonnull double[] values) {
    double max = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      if (value > max)
        max = value;
    }
    return max;
  }
}
