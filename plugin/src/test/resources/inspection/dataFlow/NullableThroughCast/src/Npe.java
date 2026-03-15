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
     Object o = foo((Object)nullable()); // null should not be passed here.
   }
}