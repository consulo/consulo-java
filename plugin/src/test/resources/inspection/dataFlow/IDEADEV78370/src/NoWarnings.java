public class NoWarnings {
    int f(@javax.annotation.Nullable String value)  {
        value = value == null ? "" : value;
        return value.hashCode();
    }
}
