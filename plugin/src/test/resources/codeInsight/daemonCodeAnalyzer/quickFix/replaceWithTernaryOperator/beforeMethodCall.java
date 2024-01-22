// "Replace with 'list != null ?:'" "true"

import jakarta.annotation.Nonnull;

class A{
  void test(@Nonnull List l) {
    final List list = null;
    test(li<caret>st);
  }
}