import org.jspecify.annotations.Nullable;

public class Npe {
   Object foo(Object o) {
     return o;
   }

   @Nullable
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