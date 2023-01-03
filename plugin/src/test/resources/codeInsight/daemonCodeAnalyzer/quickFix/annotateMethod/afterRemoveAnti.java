// "Annotate overridden methods as '@NotNull'" "true"

import javax.annotation.Nonnull;

public class XEM {
     <caret>@Nonnull
     String f(){
         return "";
     }
 }
 class XC extends XEM {
     @Nonnull
     String f() {
         return "";
     }
 }
