// "Bind Constructor Parameters to Fields" "true"

import org.jspecify.annotations.Nullable;

public class TestBefore {

    public TestBefore(@Nullable String name<caret>, @Nullable String name2) {
        super();
    }
}
