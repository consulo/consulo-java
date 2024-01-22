// "Annotate overridden methods as '@NotNull'" "true"
import jakarta.annotation.Nonnull;

abstract class P2 {
    @Nonnull
	<caret>
    String foo(@Nonnull P p) {
        return "";
    }
}

class PPP extends P2 {
    @Nonnull
    String foo(P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    @Nonnull
    String foo(P p) {
        return super.foo(p);
    }
}