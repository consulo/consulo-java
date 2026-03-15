import org.jspecify.annotations.Nullable;

abstract class P2 {
    String foo(@jakarta.annotation.Nonnull P p) {
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

///////  in library
interface Foo {
    String getTitle();
}
class FooImpl extends java.awt.Frame implements Foo {
//    public String getTitle() {
//        return super.getTitle();    //To change body of overridden methods use File | Settings | File Templates.
//    }
}


interface I1 {
  @Nullable
  Object foo();
}

interface I2 extends I1 {
  @jakarta.annotation.Nonnull
  Object foo();
}

class A implements I1 {
  @Override
  public Object foo() {
    // returns something
  }
}

class B extends A implements I2 {
}