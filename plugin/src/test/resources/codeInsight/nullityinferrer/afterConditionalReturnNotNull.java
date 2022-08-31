import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class Test {
    @Nonnull
    public  String noNull( @Nullable String text) {
        return text == null ? "" : text;
    }
}