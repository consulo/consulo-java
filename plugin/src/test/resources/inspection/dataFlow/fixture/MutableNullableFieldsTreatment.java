import jakarta.annotation.Nullable;

class Foo {
  @Nullable Object data;

  void checkNotNullAndUse(Foo f) {
    if (f.data != null) {
      System.out.println(f.data.hashCode());
    }
  }

  void checkNullAndReturn(Foo f) {
    if (f.data == null) {
      return;
    }
    System.out.println(f.data.hashCode());
  }

  void warnWhenWrongCheck() {
    if (data != null) {
      return;
    }
    System.out.println(<warning descr="Method invocation 'data.hashCode()' may produce 'java.lang.NullPointerException'">data.hashCode()</warning>);
    System.out.println(data.hashCode());
  }

  void warnWhenNotCheckedOnce(Foo f) {
    System.out.println(<warning descr="Method invocation 'f.data.hashCode()' may produce 'java.lang.NullPointerException'">f.data.hashCode()</warning>);
    System.out.println(f.data.hashCode());
  }
  
  void warnWhenNotCheckedThisOnce() {
    System.out.println(<warning descr="Method invocation 'data.hashCode()' may produce 'java.lang.NullPointerException'">data.hashCode()</warning>);
    System.out.println(data.hashCode());
  }
  
  void warnWhenDoubleChecked(Foo f) {
    if (f.data == null) {
      return;
    }
    if (<warning descr="Condition 'f.data == null' is always 'false'">f.data == null</warning>) {
      return;
    }
    System.out.println(f.data.hashCode());
  }

  void dontWarnWhenDoubleChecked_This_Synchronized() {
    if (data == null) {
      return;
    }
    synchronized (this) {
      if (data == null) {
        return;
      }
    }
    System.out.println(data.hashCode());
  }
  
  void warnWhenDoubleChecked_This_WithMethodCall() {
    if (data == null) {
      return;
    }
    System.out.println(data.hashCode());
    if (<warning descr="Condition 'data == null' is always 'false'">data == null</warning>) {
      return;
    }
    System.out.println(data.hashCode());
  }

}