import javax.annotation.Nonnull;
public class Test {
    @Nonnull
    public Object foo() {
        return new Object();
    }

    public void qqq() {
        int c = foo() != null ? foo().hashCode() : 0;
    }
}
