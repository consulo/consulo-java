

public class Npe {
   void bar() {
     final @javax.annotation.Nullable Object o = foo();
     o.hashCode(); // NPE
   }

   @javax.annotation.Nullable
   Object foo() {
     return null;
   }
}
