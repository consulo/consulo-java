import org.jspecify.annotations.Nullable;

class Test {
    public  String noNull( @Nullable String text) {
        return text == null ? "" : text;
    }
}