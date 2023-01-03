// "Annotate overridden methods as '@NotNull'" "true"

import javax.annotation.*;

public class XEM {
     <caret>@Nonnull
     String f(){
         return "";
     }
 }
 class XC extends XEM {
     @javax.annotation.Nullable
     String f() {
         return "";
     }
 }
