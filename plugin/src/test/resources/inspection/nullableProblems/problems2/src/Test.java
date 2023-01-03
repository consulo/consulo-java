import javax.annotation.*;

class C {
  public static C C = null;
  @Nonnull
  public C getC() {return C;}
  @Nonnull
  public C getC2() {return C;}

  public void f1(@javax.annotation.Nullable C p) {}
  public void f2(@Nonnull C p) {}
  public void f3(@javax.annotation.Nullable C p) {}
  public void f4(@Nonnull C p) {}
}

class CC extends C {
  @javax.annotation.Nullable
  public C getC() {return C;}
  public C getC2() {return C;}

  public void f1(@Nonnull C p) {}
  public void f2(@Nonnull C p) {}
  public void f3(@javax.annotation.Nullable C p) {}
  public void f4(@javax.annotation.Nullable C p) {}

  @javax.annotation.Nullable
  @Nonnull
  String f() { return null;}
}
