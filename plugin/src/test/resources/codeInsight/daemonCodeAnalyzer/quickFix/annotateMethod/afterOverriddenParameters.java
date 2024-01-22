// "Annotate overridden method parameters as '@NotNull'" "true"
import jakarta.annotation.Nonnull;

abstract class P2 {
    @jakarta.annotation.Nonnull
    String foo(@Nonnull<caret> P p) {
        return "";
    }
}

class PPP extends P2 {
    String foo(@jakarta.annotation.Nonnull P p) {
        return super.foo(p);
    }
}
class PPP2 extends P2 {

    String foo(@jakarta.annotation.Nonnull P p) {
        return super.foo(p);
    }
}