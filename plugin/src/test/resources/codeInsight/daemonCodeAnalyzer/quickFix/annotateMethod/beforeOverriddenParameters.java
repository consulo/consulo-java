// "Annotate overridden method parameters as '@NotNull'" "true"


abstract class P2 {
    String foo(@jakarta.annotation.Nonnull<caret> P p) {
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