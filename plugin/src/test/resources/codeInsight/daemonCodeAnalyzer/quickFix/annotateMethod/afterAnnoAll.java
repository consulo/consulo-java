// "Annotate method as '@NotNull'" "true"
import jakarta.annotation.Nonnull;

class X {
    @jakarta.annotation.Nonnull
    String annotateBase() {
        return "X";
    }
}
class Y extends X{
    @jakarta.annotation.Nonnull
    String annotateBase() {
        return "Y";
    }
}
class Z extends Y {
    @Nonnull
    String annotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
