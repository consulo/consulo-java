// "Bind Constructor Parameters to Fields" "true"

import javax.annotation.Nonnull;

public class TestBefore {

    public TestBefore(@Nonnull String name<caret>, @Nonnull String name2) {
        super();
    }
}
