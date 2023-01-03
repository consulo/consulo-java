import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Main {
  @Nonnull
  private Object test1(@Nonnull Object defVal, @Nullable final Object val) {
    return defVal;
  }
  @Nonnull
  private Object test11(@Nonnull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test5(@Nonnull Object defVal, @Nullable final Object val) {
    if (defVal == val) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test6(@Nonnull Object defVal, @Nullable final Object val) {
    if (val == defVal) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test7(@Nonnull Object defVal, @Nullable final Object val) {
    if (<warning descr="Method invocation 'val.equals(defVal)' may produce 'java.lang.NullPointerException'">val.equals(defVal)</warning>) {
      return defVal;
    }
    return defVal;
  }
  @Nonnull
  private Object test8(@Nonnull Object defVal, @javax.annotation.Nullable final Object val) {
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test9(@Nonnull Object defVal, @javax.annotation.Nullable final Object val) {
    if (equals(val)) {
      return val;
    }
    return defVal;
  }
  @Nonnull
  private Object test10(@Nonnull Object defVal, @Nullable final Object val) {
    if (val != null) {
      return val;
    }
    if (defVal.equals(val)) {
      return val;
    }
    return defVal;
  }

  @Nonnull
  private static Object test(@Nonnull Object defVal, @javax.annotation.Nullable final Object val) {
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