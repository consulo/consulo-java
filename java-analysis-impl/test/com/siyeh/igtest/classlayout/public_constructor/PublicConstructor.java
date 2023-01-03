package com.siyeh.igtest.classlayout.public_constructor;

import java.io.Externalizable;

public class PublicConstructor {}
abstract class X implements Externalizable {
  public X() {}
}
class Y {
  public Y() {}
}
abstract class Z {
  public Z() {}
}