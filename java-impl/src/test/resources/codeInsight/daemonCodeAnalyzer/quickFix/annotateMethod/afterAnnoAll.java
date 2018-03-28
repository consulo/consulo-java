// "Annotate method as '@NotNull'" "true"
import javax.annotation.Nonnull;

class X {
    @Nonnull
    String annotateBase() {
        return "X";
    }
}
class Y extends X{
    @Nonnull
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
