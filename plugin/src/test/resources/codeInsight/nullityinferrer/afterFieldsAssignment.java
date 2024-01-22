import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class Test {
  @jakarta.annotation.Nonnull
  String myFoo = "";

  @jakarta.annotation.Nullable
  String myFoo1 = null;

  @jakarta.annotation.Nonnull
  String myFoo2 = foo2();
  @jakarta.annotation.Nonnull
  String foo2() { return "";}

  @jakarta.annotation.Nullable
  String myFoo3 = foo3();
  @Nullable
  String foo3() { return null;}

  String myFoo4;
  void setFoo4() {
    myFoo4 = "";
  }

  @jakarta.annotation.Nonnull
  final String myFoo5;
  @jakarta.annotation.Nullable
  final String myFoo6;
  @jakarta.annotation.Nonnull
  final String myFoo7;
  @jakarta.annotation.Nullable
  final String myFoo8;
  final String myFoo9;
  @jakarta.annotation.Nullable
  final String myFoo10;

  final String myFoo11 = "";
  @Nonnull
  final String myFoo12;
  @jakarta.annotation.Nullable
  final String myFoo13 = null;

  /**
   * {@link #myFoo6}
   */
  Test(@Nonnull String param, @jakarta.annotation.Nullable String paramNullable, String simpleParam) {
    myFoo5 = "";
    myFoo6 = null;
    myFoo7 = param;
    myFoo8 = paramNullable;
    myFoo9 = simpleParam;
    myFoo10 = foo10(false);
    myFoo12 = "";
  }

  @jakarta.annotation.Nullable
  String foo10(boolean flag) {
    return flag ? foo2() : foo3();
  }
}