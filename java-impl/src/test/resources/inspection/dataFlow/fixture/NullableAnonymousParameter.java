public class Bar {

    void navigateTo(final@javax.annotation.Nullable Object p) {
      if (p == null) {
        return;
      }

        Runnable c = new Runnable() {
            public void run() {
                p.hashCode();
            }
        };
    }

}