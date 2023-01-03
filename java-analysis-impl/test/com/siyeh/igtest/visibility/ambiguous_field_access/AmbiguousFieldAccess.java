package com.siyeh.igtest.visibility.ambiguous_field_access;

import java.util.List;

public class AmbiguousFieldAccess {
}
class Foo { protected String name;  public void set(String s){} }
class Bar {

  public void set(String s) {}

  private String name;
  void foo(List<String> name) {
    for(String name1: name) {
      doSome(new Foo() {{
        set(name);
      }});
    }
  }

  private void doSome(Foo foo) {
  }
}