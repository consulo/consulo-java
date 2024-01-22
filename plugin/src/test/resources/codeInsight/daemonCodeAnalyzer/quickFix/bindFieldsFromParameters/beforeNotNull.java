// "Bind Constructor Parameters to Fields" "true"

import jakarta.annotation.Nonnull;

public class TestBefore {

    public TestBefore(@Nonnull String name<caret>, @jakarta.annotation.Nonnull String name2) {
        super();
    }
}
