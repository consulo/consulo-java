// "Bind Constructor Parameters to Fields" "true"

import javax.annotation.Nonnull;

public class TestBefore {

    @Nonnull
    private final String myName;
    @Nonnull
    private final String myName2;

    public TestBefore(@Nonnull String name, @Nonnull String name2) {
        super();
        myName = name;
        myName2 = name2;
    }
}
