// "Annotate method as '@NotNull'" "true"
import javax.annotation.Nonnull;

class X {
    @Nonnull
    String annotateBase() {
        return "X";
    }
}
class Y extends X{
    String annotateBase() {
        return "Y";
    }
}
class Z extends Y {
    String annotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
