import jakarta.annotation.Nonnull;

import javax.swing.*;

class Test {
    public void setObj(Object obj) {
        this.obj = obj;
    }

    public void test() {
        obj = new Object();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Object o = obj;
                if (<warning descr="Condition 'o != null' is always 'true'">o != null</warning>) {
                    System.out.println("x");
                }
            }
        });
      obj = <warning descr="'null' is assigned to a variable that is annotated with @NotNull">null</warning>;
    }

    @Nonnull
	private volatile Object obj;
}