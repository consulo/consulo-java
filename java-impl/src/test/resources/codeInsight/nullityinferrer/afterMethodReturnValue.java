import javax.annotation.*;

class Test {
  @javax.annotation.Nullable
  String foo1() {
    return null;
  }

  @Nonnull
  String foo2() {
    return "";
  }

  String foo3(String s) {
    return s;
  }

  String foo4(@Nonnull String s) {
    return s.substring(0);
  }

  @Nonnull
  Integer foo5(Integer i) {
    return i++;
  }

  @Nonnull
  Integer foo6(Integer i) {
    if (i == 0) return 1;
    return i * foo6(i--);
  }

  @javax.annotation.Nullable
  Integer foo7(boolean flag) {
    return flag ? null : 1;
  }

  @javax.annotation.Nullable
  Integer foo8(boolean flag) {
    if (flag) {
      return null;
    }
    else {
      return 1;
    }
  }

  @javax.annotation.Nullable
  String bar9() {
    return foo3("");
  }

  @javax.annotation.Nullable
  String foo9() {
    return bar9();
  }


  @javax.annotation.Nullable
  String bar10() {
    return foo3("");
  }

  @Nonnull
  String bar101() {
    return foo3("");
  }

  @javax.annotation.Nullable
  String foo10(boolean flag) {
    return flag ? bar10() : bar101();
  }

  @Nonnull
  String foo11() {
    class Foo{
      @javax.annotation.Nullable
      String mess() {
        return null;
      }
    }
    return "";
  }
}