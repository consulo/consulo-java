import jakarta.annotation.Nullable;

public class Npe {
  
  void foo(@Nullable SomeInterface x) {
    ((SomeInterfaceImpl)x).bar();
  }

  interface SomeInterface {
    void bar();
  }

  class SomeInterfaceImpl implements SomeInterface {
    @Override
    public void bar() {
    }
  }
}