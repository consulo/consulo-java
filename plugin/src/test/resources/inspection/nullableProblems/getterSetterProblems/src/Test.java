import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class B {
     @Nonnull
     B b;

    public B getB() {
        return b;
    }

    public void setB(B b) {
        this.b = b;
    }

        @jakarta.annotation.Nonnull
        private String bug = "true";

        public boolean getBug() {
            return Boolean.valueOf(bug);
        }
}
class C {
  @jakarta.annotation.Nonnull
  C c;

  C(C c) {
    this.c = c;
  }

  C(@jakarta.annotation.Nullable C c, int i) {
    this.c = c;
  }

  @jakarta.annotation.Nullable
  public C getC() {
    return c;
  }

  public void setC(@Nullable C c) {
    this.c = c;
  }

  @Nonnull
  C c1;
  @jakarta.annotation.Nullable
  public C getC1() {
    if (c1 != null) {
      return null;
    }
    return c1;
  }
}

class D {
    @jakarta.annotation.Nullable
    Long myL;

    D(long l) {
      myL = l;
    }
}

class E {
  final @jakarta.annotation.Nonnull
  C c;

  E(C c) {
    this.c = c;
  }

}