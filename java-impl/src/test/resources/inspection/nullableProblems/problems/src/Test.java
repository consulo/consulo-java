import javax.annotation.Nonnull;

class B {
    public void f(@Nonnull String p){}
    @Nonnull
    public String nn(@javax.annotation.Nullable String param) {
        return "";
    }
}
         
public class Y extends B {
    @Nonnull
	@javax.annotation.Nullable
	String s;
    public void f(String p){}
       
          
    public String nn(@Nonnull String param) {
        return "";
    }
    void p(@Nonnull @javax.annotation.Nullable String p2){}


    @javax.annotation.Nullable
	int f;
    @Nonnull
	void vf(){}
    void t(@Nonnull double d){}
}
