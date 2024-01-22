import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class Test {
    @Nonnull
    public  String noNull( @Nullable String text) {
        return text == null ? "" : text;
    }
}