// "Bind Constructor Parameters to Fields" "true"

import jakarta.annotation.Nullable;

public class TestBefore {

    @Nullable
    private final String myName;
    @jakarta.annotation.Nullable
    private final String myName2;

    public TestBefore(@jakarta.annotation.Nullable String name, @jakarta.annotation.Nullable String name2) {
        super();
        myName = name;
        myName2 = name2;
    }
}
