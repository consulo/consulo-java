import org.jspecify.annotations.Nullable;

public class Npe {
   Object aField;
   @Nullable
   Object nullable() {
     return null;
   }

   void bar() {
     Object o = nullable();
     aField = o;
     Object aLocalVariable = o;
   }
}