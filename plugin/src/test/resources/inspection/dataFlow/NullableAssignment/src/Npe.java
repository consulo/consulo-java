import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class Npe {
   @Nonnull
   Object aField;
   @Nullable
   Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     aField = o;
     @Nonnull Object aLocalVariable = o;
   }
}