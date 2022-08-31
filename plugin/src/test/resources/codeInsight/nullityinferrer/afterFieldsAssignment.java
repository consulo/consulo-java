import javax.annotation.*;

class Test {
  @Nonnull
  String myFoo = "";

  @javax.annotation.Nullable
  String myFoo1 = null;

  @Nonnull
  String myFoo2 = foo2();
  @Nonnull
  String foo2() { return "";}

  @javax.annotation.Nullable
  String myFoo3 = foo3();
  @javax.annotation.Nullable
  String foo3() { return null;}

  String myFoo4;
  void setFoo4() {
    myFoo4 = "";
  }

  @Nonnull
  final String myFoo5;
  @javax.annotation.Nullable
  final String myFoo6;
  @Nonnull
  final String myFoo7;
  @javax.annotation.Nullable
  final String myFoo8;
  final String myFoo9;
  @javax.annotation.Nullable
  final String myFoo10;

  final String myFoo11 = "";
  @Nonnull
  final String myFoo12;
  @javax.annotation.Nullable
  final String myFoo13 = null;

  /**
   * {@link #myFoo6}
   */
  Test(@Nonnull String param, @javax.annotation.Nullable String paramNullable, String simpleParam) {
    myFoo5 = "";
    myFoo6 = null;
    myFoo7 = param;
    myFoo8 = paramNullable;
    myFoo9 = simpleParam;
    myFoo10 = foo10(false);
    myFoo12 = "";
  }

  @javax.annotation.Nullable
  String foo10(boolean flag) {
    return flag ? foo2() : foo3();
  }
}