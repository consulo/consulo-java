package com.siyeh.igtest.abstraction.weaken_type;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class AutoClosableTest
{
    public static class Foo
    {
        public void go() {}
    }

    public static class Bar extends Foo implements AutoCloseable
    {
        @Override
        public void close() {}
    }

    public static void test()
    {
        try (Bar bar = new Bar()) {
            bar.go();
        }
    }
}
class AutoClosableTest2
{
    public static class Foo implements AutoCloseable
    { 
        public void close() {}
        public void go() {}
    }

    public static class Bar extends Foo {}

    public static void test() {
        try (Bar bar = new Bar()) {
            bar.go();
        }
    }

    void dodo() throws IOException {
        try (Reader  reader = new FileReader("/home/steve/foo.txt")) {
            System.out.println(reader);
        }
    }
}