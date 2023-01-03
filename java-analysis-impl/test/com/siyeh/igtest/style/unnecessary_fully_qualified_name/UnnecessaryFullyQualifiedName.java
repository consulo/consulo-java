package com.siyeh.igtest.style.unnecessary_fully_qualified_name;

import java.awt.List;
import java.io.PrintStream;
import java.lang.System;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * {@link java.lang.String}
 */
public class UnnecessaryFullyQualifiedName
{
    private String m_string1;
    private java.lang.String m_string;
    private StringTokenizer m_map;
    private List m_list;
    private Map.Entry m_mapEntry;
    private List m_awtList;
    PrintStream stream = System.out;
    Properties props = System.getProperties();

    public UnnecessaryFullyQualifiedNameInspection(java.lang.String s) {}

    class String {}

    java.util.Vector v;
    class Vector {}
    
    java.util.Set set;
    String Set;
}
enum SomeEnum {

    Foo {
        @Override
        public void perform() {
            test.Foo.perform();
        }
    };

    public abstract void perform();

    private List spaces;
}