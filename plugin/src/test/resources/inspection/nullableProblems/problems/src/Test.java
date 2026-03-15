import org.jspecify.annotations.Nullable;

class B {
    public void f(String p){}
    @jakarta.annotation.Nonnull
    public String nn(@Nullable String param) {
        return "";
    }
}
         
public class Y extends B {
    @jakarta.annotation.Nonnull
	@Nullable
	String s;
    public void f(String p){}
       
          
    public String nn(String param) {
        return "";
    }
    void p(@jakarta.annotation.Nonnull @Nullable String p2){}


    @Nullable
	int f;
	void vf(){}
    void t(double d){}
}
