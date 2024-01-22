package com.siyeh.igtest.encapsulation.public_field;

import jakarta.annotation.Nullable;

public class PublicField {
  public final X y = X.Y;
  public String s = "";
  @Nullable
  public String t = "";
  public static final String LEGAL = "legal";


  public enum X {
    Y
  }
}
