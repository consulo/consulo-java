// "Annotate method as '@NotNull'" "true"

import jakarta.annotation.Nonnull;

class X {
    @jakarta.annotation.Nonnull
    String dontAnnotateBase() {
        return "X";
    }
}
class Y extends X{
    String dontAnnotateBase() {
        return "Y";
    }
}
class Z extends Y {
    @Nonnull
    String dontAnnotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
