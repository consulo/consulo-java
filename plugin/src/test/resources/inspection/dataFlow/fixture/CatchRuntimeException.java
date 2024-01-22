import jakarta.annotation.Nonnull;

public class BrokenAlignment {

  @Nonnull
  Object test1() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (RuntimeException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  @Nonnull
  Object test2() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (IllegalArgumentException | IllegalStateException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  @Nonnull
  Object test3() {
    try {
      bar(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
    catch (AssertionError | IllegalStateException e) {
      return <warning descr="'null' is returned by the method declared as @NotNull">null</warning>;
    }
  }

  public void bar(@Nonnull Object foo) {
    assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>;
  }

  public void bar2(@Nonnull Object foo) {
    assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>;
    try { }
    catch (java.lang.RuntimeException ex) { }
  }

}