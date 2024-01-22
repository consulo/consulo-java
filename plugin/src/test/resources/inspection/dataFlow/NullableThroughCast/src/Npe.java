import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class Npe {
   Object foo(@Nonnull Object o) {
     return o;
   }

   @Nullable
   Object nullable() {
     return null;
   }

   void bar() {
     Object o = foo((Object)nullable()); // null should not be passed here.
   }
}