import jakarta.annotation.Nullable;

class C {
  public static C C = null;
  @jakarta.annotation.Nonnull
  public C getC() {return C;}
  @jakarta.annotation.Nonnull
  public C getC2() {return C;}

  public void f1(@Nullable C p) {}
  public void f2(@jakarta.annotation.Nonnull C p) {}
  public void f3(@jakarta.annotation.Nullable C p) {}
  public void f4(@jakarta.annotation.Nonnull C p) {}
}

class CC extends C {
  @jakarta.annotation.Nullable
  public C getC() {return C;}
  public C getC2() {return C;}

  public void f1(@jakarta.annotation.Nonnull C p) {}
  public void f2(@jakarta.annotation.Nonnull C p) {}
  public void f3(@jakarta.annotation.Nullable C p) {}
  public void f4(@jakarta.annotation.Nullable C p) {}

  @jakarta.annotation.Nullable
  @jakarta.annotation.Nonnull
  String f() { return null;}
}
