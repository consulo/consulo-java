import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class A {
    @Nullable
    public Object methodFromA(@Nonnull String s) {
        return null;
    }
}
