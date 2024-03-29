package com.siyeh.igtest.bugs;

import java.lang.Object;

public class PrimitiveArrayArgumentToVariableArgMethod {

    static void bar(Object... objects) {
        for (Object object : objects) {
            System.out.println("object: " + object);
        }
    }

    static void foo(int... ints) {
        for (int anInt : ints) {
            System.out.print(anInt);
        }
    }

    public static void main(String[] args) {
        int[] ints = {1, 2, 3};
        bar(ints); // warn here
        foo(ints); // no warning needed here
    }
}