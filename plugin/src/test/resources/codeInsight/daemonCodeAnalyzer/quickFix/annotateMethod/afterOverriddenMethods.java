// "Annotate overridden methods as '@NotNull'" "true"

abstract class P2 {
	<caret>
    String foo(P p) {
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