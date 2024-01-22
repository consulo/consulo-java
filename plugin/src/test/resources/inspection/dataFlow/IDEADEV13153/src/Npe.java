import jakarta.annotation.Nonnull;

class Test {
   void foo() {
     String[] data = new String[] {"abs", "def"};
     for (@Nonnull String foo: data) {
       assert foo != null; // Condition always true
     }
   }
}