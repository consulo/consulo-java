import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.Object;

public class Doo {
  private final Object myA;
  private final Object myB;
  private final Object myC;

  public Doo(@Nullable Object myA, @Nonnull Object myB, Object c) {
    this.myA = myA;
    this.myB = myB;
    myC = c;
  }

  int bar() {
    return myC.hashCode();
  }


  int foo() {
    if (<warning descr="Condition 'myB != null' is always 'true'">myB != null</warning> &&
    <warning descr="Method invocation 'myA.equals(myB)' may produce 'java.lang.NullPointerException'">myA.equals(myB)</warning>) {
      return 2;
    }

    return myA.hashCode();
  }
}
