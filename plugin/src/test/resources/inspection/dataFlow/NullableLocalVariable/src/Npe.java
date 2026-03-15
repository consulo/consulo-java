import org.jspecify.annotations.Nullable;

public class Npe {
   void bar() {
     final @Nullable Object o = foo();
     o.hashCode(); // NPE
   }

   @Nullable
   Object foo() {
     return null;
   }
}
