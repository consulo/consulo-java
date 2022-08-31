// "Create Field for Parameter 'name'" "true"

import javax.annotation.Nonnull;

public class TestBefore {

    @Nonnull
    private final String myName;

    public TestBefore(@Nonnull String name) {
        super();
        myName = name;
    }
}
