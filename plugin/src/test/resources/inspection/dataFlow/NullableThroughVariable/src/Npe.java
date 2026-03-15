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
     foo(o); // null should not be passed here
   }
}