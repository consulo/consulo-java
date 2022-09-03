package com.siyeh.igtest.performance.method_may_be_static;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

public class A implements Serializable {

    private void writeObject(ObjectOutputStream out)
            throws IOException {
        System.out.println("out");
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        System.out.println();
    }

    Object writeReplace() throws ObjectStreamException {
        return null;
    }

    Object readResolve() throws ObjectStreamException {
        return null;
    }
    native void f();
    void g() {
        System.out.println("boo!");
    }
}
class C {
  public int getInt() { return 5; }
}
class D extends C implements Surprise {
}
interface Surprise {
  int getInt();
}