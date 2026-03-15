// "Annotate overridden methods as '@NotNull'" "true"

import org.jspecify.annotations.Nullable;

public class XEM {
     <caret>@jakarta.annotation.Nonnull
     String f(){
         return "";
     }
 }
 class XC extends XEM {
     @Nullable
     String f() {
         return "";
     }
 }
