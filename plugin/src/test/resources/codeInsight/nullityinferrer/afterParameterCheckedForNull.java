import javax.annotation.*;

class Test {
  void bar(@javax.annotation.Nullable String str) {
    if (str == null) {
      foo(str);
    }
  }

  String foo(String str) {
    return str;
  }

  @javax.annotation.Nullable
  String foo1(@javax.annotation.Nullable String str) {
    if (str == null);
    return (str);
  }

  @Nonnull
  String foo2(@javax.annotation.Nullable String str) {
    if (str == null);
    return ((String)str);
  }

  @Nonnull
  String fram(@javax.annotation.Nullable String str, boolean b) {
    if (str != null) {
      return b ? str : "not null strimg";
    }
    return "str was null";
  }




}