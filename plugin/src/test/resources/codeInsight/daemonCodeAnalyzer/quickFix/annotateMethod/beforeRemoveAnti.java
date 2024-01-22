// "Annotate overridden methods as '@NotNull'" "true"

import jakarta.annotation.Nullable;

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
