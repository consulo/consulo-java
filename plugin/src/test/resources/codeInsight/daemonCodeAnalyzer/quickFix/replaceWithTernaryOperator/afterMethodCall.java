// "Replace with 'list != null ?:'" "true"


class A{
  void test(List l) {
    final List list = null;
    test(list != null ? list : <selection>null</selection>);
  }
}