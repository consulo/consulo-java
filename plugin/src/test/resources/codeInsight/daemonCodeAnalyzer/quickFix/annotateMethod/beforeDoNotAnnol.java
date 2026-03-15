// "Annotate method as '@NotNull'" "true"

class X {
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
    String dontAnnotateBase<caret>() { // trigger quick fix for inspection here
        return "Z";
    }
}
