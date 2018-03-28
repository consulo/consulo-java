import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class B {
    public void f(@Nonnull String p){}
    @Nonnull
    public String nn(@Nullable String param) {
        return "";
    }
}
         
public class Y extends B {
    @Nonnull
	@Nullable String s;
    public void f(String p){}
       
          
    public String nn(@Nonnull String param) {
        return "";
    }
    void p(@Nonnull @Nullable String p2){}


    @Nullable int f;
    @Nonnull
	void vf(){}
    void t(@Nonnull double d){}
}
