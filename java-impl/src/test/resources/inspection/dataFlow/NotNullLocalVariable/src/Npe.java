import javax.annotation.Nonnull;

public class Npe {
   void bar() {
     final @Nonnull Object o = call();
     if (o == null) {}
   }
   Object call() {return new Object();}
}