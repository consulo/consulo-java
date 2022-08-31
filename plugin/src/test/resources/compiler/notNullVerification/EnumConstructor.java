import javax.annotation.Nonnull;

public enum EnumConstructor {
    Value("label");

    private final String label;

    EnumConstructor(@Nonnull String label) {
        this.label = label;
    }
}
