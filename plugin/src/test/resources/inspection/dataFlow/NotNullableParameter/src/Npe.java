import jakarta.annotation.Nonnull;

public class Npe {
   Object foo(@Nonnull Object o) {
     return o;
   }

   void bar() {
     Object o = foo(null); // null should not be passed here.
   }
}