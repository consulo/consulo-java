package com.siyeh.igtest.classlayout.final_private_method;

import java.lang.SafeVarargs;

public class FinalPrivateMethod {

  private final void foo() {};

  @SafeVarargs
  private final void foo(String s) {}

  @SafeVarargs
  private final void foo(int... i) {}
}
