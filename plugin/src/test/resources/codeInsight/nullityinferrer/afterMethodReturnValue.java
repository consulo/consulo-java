import jakarta.annotation.Nullable;

class Test {
  @jakarta.annotation.Nullable
  String foo1() {
    return null;
  }

  @jakarta.annotation.Nonnull
  String foo2() {
    return "";
  }

  String foo3(String s) {
    return s;
  }

  String foo4(@jakarta.annotation.Nonnull String s) {
    return s.substring(0);
  }

  @jakarta.annotation.Nonnull
  Integer foo5(Integer i) {
    return i++;
  }

  @jakarta.annotation.Nonnull
  Integer foo6(Integer i) {
    if (i == 0) return 1;
    return i * foo6(i--);
  }

  @jakarta.annotation.Nullable
  Integer foo7(boolean flag) {
    return flag ? null : 1;
  }

  @jakarta.annotation.Nullable
  Integer foo8(boolean flag) {
    if (flag) {
      return null;
    }
    else {
      return 1;
    }
  }

  @jakarta.annotation.Nullable
  String bar9() {
    return foo3("");
  }

  @jakarta.annotation.Nullable
  String foo9() {
    return bar9();
  }


  @jakarta.annotation.Nullable
  String bar10() {
    return foo3("");
  }

  @jakarta.annotation.Nonnull
  String bar101() {
    return foo3("");
  }

  @Nullable
  String foo10(boolean flag) {
    return flag ? bar10() : bar101();
  }

  @jakarta.annotation.Nonnull
  String foo11() {
    class Foo{
      @jakarta.annotation.Nullable
      String mess() {
        return null;
      }
    }
    return "";
  }
}