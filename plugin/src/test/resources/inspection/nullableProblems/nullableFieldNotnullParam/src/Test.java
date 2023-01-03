import javax.annotation.*;

class Test {
     @javax.annotation.Nullable
	 private final String baseFile;
     @javax.annotation.Nullable
	 private final String baseFile1;


     public Test(@Nonnull String baseFile) {
         this.baseFile = baseFile;
         this.baseFile1 = null;
     }

     public Test(@Nonnull String baseFile1, boolean a) {
         this.baseFile1 = baseFile1;
         if (baseFile1.contains("foo")) {
           this.baseFile = null;
         } else {
           this.baseFile = null;
         }
     }
}