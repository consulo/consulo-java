import javax.annotation.Nonnull;

public class Npe {
   Object foo(@Nonnull Object o) {
     return o;
   }

   @javax.annotation.Nullable
   Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     if (o != null) {
       foo(o); // OK, o can't be null.
     }
   }
}