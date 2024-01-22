import jakarta.annotation.Nullable;

class Test {
  void bar(@jakarta.annotation.Nullable String str) {
    if (str == null) {
      foo(str);
    }
  }

  String foo(String str) {
    return str;
  }

  @jakarta.annotation.Nullable
  String foo1(@jakarta.annotation.Nullable String str) {
    if (str == null);
    return (str);
  }

  @jakarta.annotation.Nonnull
  String foo2(@Nullable String str) {
    if (str == null);
    return ((String)str);
  }

  @jakarta.annotation.Nonnull
  String fram(@jakarta.annotation.Nullable String str, boolean b) {
    if (str != null) {
      return b ? str : "not null strimg";
    }
    return "str was null";
  }




}