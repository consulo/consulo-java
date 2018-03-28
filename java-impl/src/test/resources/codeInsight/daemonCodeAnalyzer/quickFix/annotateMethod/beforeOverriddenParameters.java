// "Annotate overridden method parameters as '@NotNull'" "true"
import javax.annotation.Nonnull;

abstract class P2 {
    @Nonnull
    String foo(@Nonnull<caret> P p) {
        return "";
    }
}

class PPP extends P2 {
    String foo(P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    String foo(P p) {
        return super.foo(p);
    }
}