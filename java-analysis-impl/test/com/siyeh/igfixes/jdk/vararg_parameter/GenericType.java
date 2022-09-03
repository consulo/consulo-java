package com.siyeh.igfixes.jdk.vararg_parameter;

import java.lang.SafeVarargs;

@SuppressWarnings("UnusedDeclaration")
public class GenericType {
  @SafeVarargs
  final void addCl<caret>asses(Class<? extends Number>... classes) {
  }

  void test() {
    addClasses(Number.class, Byte.class);
  }
}