import jakarta.annotation.Nonnull;

public class Npe {
   @Nonnull
   Object foo() {
     return new Object();
   }

   void bar() {
     Object o = foo();
     if (o == null) System.out.println("Can't be");
   }
}