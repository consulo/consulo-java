import javax.annotation.Nonnull;

public class Npe {
   @Nonnull
   Object aField;
   @javax.annotation.Nullable
   Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     aField = o;
     @Nonnull Object aLocalVariable = o;
   }
}