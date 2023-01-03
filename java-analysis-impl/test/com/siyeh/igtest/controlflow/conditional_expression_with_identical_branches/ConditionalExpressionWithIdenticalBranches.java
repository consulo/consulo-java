package com.siyeh.igtest.controlflow.conditional_expression_with_identical_branches;

import java.lang.String;

class ConditionalExpressionWithIdenticalBranches {

  int one(boolean b) {
    return b ? 1 + 2 + 3 : 1 + 2 + 3;
  }

  int two(boolean b) {
    return b ? 1 + 2 : 1 + 2 + 3;
  }

  Class<String> three(boolean b) {
    return b ? String.class : String.class;
  }
}