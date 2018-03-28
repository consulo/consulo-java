import javax.annotation.Nullable;

// "Implement method 'foo'" "true"
abstract class Test {
  public abstract void foo(@javax.annotation.Nullable String a);
}

class TImple extends Test {
    @Override
    public void foo(@Nullable String a) {
        <caret>
    }
}