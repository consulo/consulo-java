package p;

import jakarta.annotation.Nonnull;

class Test {

  @Nonnull
  String g<caret>et() {
    return null;
  }
}