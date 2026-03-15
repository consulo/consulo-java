import org.jspecify.annotations.Nullable;

class B {
    public final A myDelegate = new A();

    @Nullable
    public Object methodFromA(String s) {
        return myDelegate.methodFromA(s);
    }
}