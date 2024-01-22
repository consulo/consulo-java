// "Annotate method as '@NotNull'" "true"

import jakarta.annotation.Nonnull;

class X {
    @Nonnull
    String foo() {
        return "X";
    }
}
class Y extends X{
    String foo() {
        return "Y";
    }
}
class Z extends Y {
    String foo<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
