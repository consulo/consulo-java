
class Test {
   void foo() {
     String[] data = new String[] {"abs", "def"};
     for (String foo: data) {
       assert foo != null; // Condition always true
     }
   }
}