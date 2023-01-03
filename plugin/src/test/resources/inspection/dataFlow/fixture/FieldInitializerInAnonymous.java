class Zoo2 {

  void foo(@javax.annotation.Nullable Object foo) {
    if (foo == null) {
      return;
    }

    new Runnable() {
      int hc = foo.hashCode();

      public void run() {
      }
    }.run();
  }

}
