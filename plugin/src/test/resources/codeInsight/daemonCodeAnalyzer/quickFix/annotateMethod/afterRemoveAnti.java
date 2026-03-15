// "Annotate overridden methods as '@NotNull'" "true"


public class XEM {
     <caret>
     String f(){
         return "";
     }
 }
 class XC extends XEM {
     String f() {
         return "";
     }
 }
