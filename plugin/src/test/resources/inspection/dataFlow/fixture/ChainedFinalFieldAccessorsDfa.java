import jakarta.annotation.Nullable;

import java.lang.String;

public class BrokenAlignment {

  void main(Data data) {
    if (data.getText() != null) {
      System.out.println(data.getText().hashCode());
    }

    data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
    System.out.println(<warning descr="Method invocation 'data.getText().hashCode()' may produce 'java.lang.NullPointerException'">data.getText().hashCode()</warning>);

    if (data.getInner() != null) {
      System.out.println(data.getInner().hashCode());
      System.out.println(<warning descr="Method invocation 'data.getInner().getText().hashCode()' may produce 'java.lang.NullPointerException'">data.getInner().getText().hashCode()</warning>);
      if (data.getInner() != null) {
        System.out.println(data.getInner().hashCode());
      }

      data = new Data(null, <warning descr="Passing 'null' argument to non annotated parameter">null</warning>);
      System.out.println(<warning descr="Method invocation 'data.getInner().hashCode()' may produce 'java.lang.NullPointerException'">data.getInner().hashCode()</warning>);
    }
  }

  void main2(Data data) {
    if (data.getInner() != null && data.getInner().getText() != null) {
      System.out.println(data.getInner().hashCode());
      System.out.println(data.getInner().getText().hashCode());
    }
  }

  void main3(Data data) {
    if (data.getInnerOverridden() != null) {
      System.out.println(data.getInnerOverridden().hashCode());
    }
    if (data.something() != null) {
      System.out.println(<warning descr="Method invocation 'data.something().hashCode()' may produce 'java.lang.NullPointerException'">data.something().hashCode()</warning>);
    }
  }

  private static class Data {
    @Nullable final String text;
    @Nullable final Data inner;

    Data(@Nullable String text, Data inner) {
      this.text = text;
      this.inner = inner;
    }

    @jakarta.annotation.Nullable
    public String getText() {
      return text;
    }

    @Nullable
    public Data getInner() {
      return inner;
    }

    @Nullable
    public Data getInnerOverridden() {
      return inner;
    }

    @Nullable
    public String something() {
      return new String();
    }
  }

  class DataImpl extends Data {
    DataImpl(@Nullable String text, Data inner) {
      super(text, inner);
    }

    @jakarta.annotation.Nullable
    @Override
    public Data getInnerOverridden() {
      return super.getInnerOverridden();
    }
  }
}