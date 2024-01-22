import jakarta.annotation.Nonnull;

import java.util.*;
class Test {
   void foo(@Nonnull List requests){
        for (@Nonnull Object request : requests) {
          System.out.println(request.toString());
        }
    }
}