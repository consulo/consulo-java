import org.jspecify.annotations.Nullable;

public class NoWarnings {
    int f(@Nullable String value)  {
        value = value == null ? "" : value;
        return value.hashCode();
    }
}
