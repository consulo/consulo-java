
public class Npe {
   Object foo(Object o) {
     if (o == null) {
       // Should not get there.
     }
     return o;
   }
}