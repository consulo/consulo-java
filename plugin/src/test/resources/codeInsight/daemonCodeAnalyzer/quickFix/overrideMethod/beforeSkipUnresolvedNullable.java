import javax.annotation.Nullable;

// "Implement method 'foo'" "true"
abstract class Test {
  public abstract void f<caret>oo(@Nullable String a);
}

class TImple extends Test {}