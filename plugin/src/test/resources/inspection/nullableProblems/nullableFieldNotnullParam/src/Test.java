import jakarta.annotation.Nullable;

class Test {
     @Nullable
	 private final String baseFile;
     @jakarta.annotation.Nullable
	 private final String baseFile1;


     public Test(@jakarta.annotation.Nonnull String baseFile) {
         this.baseFile = baseFile;
         this.baseFile1 = null;
     }

     public Test(@jakarta.annotation.Nonnull String baseFile1, boolean a) {
         this.baseFile1 = baseFile1;
         if (baseFile1.contains("foo")) {
           this.baseFile = null;
         } else {
           this.baseFile = null;
         }
     }
}