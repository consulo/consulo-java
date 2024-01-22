import jakarta.annotation.Nullable;

public class Bar {

    void navigateTo() {
        Computable c = new Computable() {
            @jakarta.annotation.Nullable
            public Object compute() {
                return null;
            }
        };
    }

}

interface Computable {
    @Nullable
    Object compute();
}