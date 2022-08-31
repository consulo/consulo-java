import javax.annotation.Nonnull;

class B {
    public final A myDelegate = new A();

    @javax.annotation.Nullable
    public Object methodFromA(@Nonnull String s) {
        return myDelegate.methodFromA(s);
    }
}