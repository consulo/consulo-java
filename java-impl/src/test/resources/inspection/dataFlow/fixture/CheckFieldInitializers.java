import javax.annotation.Nonnull;


class Foo {
  @javax.annotation.Nullable
  static Object foo() { return null; }
  static String bar(@Nonnull Object arg) { return ""; }
}
class Bar {
  public static final String s = Foo.bar(<warning descr="Argument 'Foo.foo()' might be null">Foo.foo()</warning>);
  @Nonnull
  public static Object o = <warning descr="Expression 'Foo.foo()' might evaluate to null but is assigned to a variable that is annotated with @NotNull">Foo.foo()</warning>;

}