import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BrokenAlignment {
  void t() {
    @Nonnull Collection list = new ArrayList();
    List<String> strings = (List<String>) list;
    if (<warning descr="Condition 'strings != null' is always 'true'">strings != null</warning>) {
      int foo = 42;
    }
  }

}