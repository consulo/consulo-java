
public class Npe {
   void bar() {
     final Object o = call();
     if (o == null) {}
   }
   Object call() {return new Object();}
}