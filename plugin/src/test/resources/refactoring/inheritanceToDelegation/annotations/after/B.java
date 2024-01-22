import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class B {
    public final A myDelegate = new A();

    @Nullable
    public Object methodFromA(@Nonnull String s) {
        return myDelegate.methodFromA(s);
    }
}