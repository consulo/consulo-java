package p;

import javax.annotation.Nonnull;

class Test {

  @Nonnull
  String g<caret>et() {
    return null;
  }
}