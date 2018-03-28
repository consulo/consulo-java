package com.siyeh.igtest.encapsulation.public_field;

public class PublicField {
  public final X y = X.Y;
  public String s = "";
  @javax.annotation.Nullable
  public String t = "";
  public static final String LEGAL = "legal";


  public enum X {
    Y
  }
}
