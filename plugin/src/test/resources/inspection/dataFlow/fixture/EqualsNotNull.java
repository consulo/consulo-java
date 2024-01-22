import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class Main {
  @Nonnull
  private Object test1(@jakarta.annotation.Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    return defVal;
  }
  @Nonnull
  private Object test11(@Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (val != null) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test5(@jakarta.annotation.Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (defVal == val) {
      return val;
    }
    return defVal;
  }
  @jakarta.annotation.Nonnull
  private Object test6(@jakarta.annotation.Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (val == defVal) {
      return val;
    }
    return defVal;
  }
  @jakarta.annotation.Nonnull
  private Object test7(@Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (<warning descr="Method invocation 'val.equals(defVal)' may produce 'java.lang.NullPointerException'">val.equals(defVal)</warning>) {
      return defVal;
    }
    return defVal;
  }
  @jakarta.annotation.Nonnull
  private Object test8(@jakarta.annotation.Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }
  @jakarta.annotation.Nonnull
  private Object test9(@jakarta.annotation.Nonnull Object defVal, @Nullable final Object val) {
    if (equals(val)) {
      return val;
    }
    return defVal;
  }
  @jakarta.annotation.Nonnull
  private Object test10(@jakarta.annotation.Nonnull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }

  @jakarta.annotation.Nonnull
  private static Object test(@jakarta.annotation.Nonnull Object defVal, @jakarta.annotation.Nullable final Object val) {
    if (val != null) {
      return val;
    }
    if (<warning descr="Condition 'defVal == val' is always 'false'">defVal == val</warning>) {
      return val;
    }
    if (<warning descr="Condition 'val == defVal' is always 'false'">val == defVal</warning>) {
      return val;
    }
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }

}