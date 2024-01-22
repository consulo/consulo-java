import jakarta.annotation.Nullable;

public class NoWarnings {
    int f(@Nullable String value)  {
        value = value == null ? "" : value;
        return value.hashCode();
    }
}
