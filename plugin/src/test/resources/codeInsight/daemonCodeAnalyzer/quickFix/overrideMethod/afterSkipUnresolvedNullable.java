import jakarta.annotation.Nullable;

// "Implement method 'foo'" "true"
abstract class Test {
  public abstract void foo(@Nullable String a);
}

class TImple extends Test {
    @Override
    public void foo(@Nullable String a) {
        <caret>
    }
}