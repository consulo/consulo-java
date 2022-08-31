public class Foo {
    @javax.annotation.Nullable
	Foo foo() {
        return null;
    }

    public void bar() {
        if (foo() != null &&
          foo().foo() != null &&
          foo().foo().foo() != null) {

        }
    }
}
