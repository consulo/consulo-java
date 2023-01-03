import javax.annotation.Nullable;

public class Bar {

    void navigateTo() {
        Computable c = new Computable() {
            @javax.annotation.Nullable
            public Object compute() {
                return null;
            }
        };
    }

}

interface Computable {
    @Nullable Object compute();
}