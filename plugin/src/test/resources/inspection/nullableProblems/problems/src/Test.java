import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class B {
    public void f(@Nonnull String p){}
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
       
          
    public String nn(@Nonnull String param) {
        return "";
    }
    void p(@jakarta.annotation.Nonnull @Nullable String p2){}


    @Nullable
	int f;
    @Nonnull
	void vf(){}
    void t(@Nonnull double d){}
}
