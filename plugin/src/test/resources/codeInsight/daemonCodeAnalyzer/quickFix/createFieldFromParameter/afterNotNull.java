// "Create Field for Parameter 'name'" "true"

import jakarta.annotation.Nonnull;

public class TestBefore {

    @jakarta.annotation.Nonnull
    private final String myName;

    public TestBefore(@Nonnull String name) {
        super();
        myName = name;
    }
}
