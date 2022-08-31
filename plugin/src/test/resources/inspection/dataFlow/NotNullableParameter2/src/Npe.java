import javax.annotation.Nonnull;

public class Npe {
   Object foo(@Nonnull Object o) {
     if (o == null) {
       // Should not get there.
     }
     return o;
   }
}