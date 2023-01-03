package com.siyeh.igtest.classlayout.emptyclass;

import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;

public class EmptyClass {
    {
      final ArrayList<String> stringList = new ArrayList<String>() {};
      System.out.println("");
    }
}
class MyList extends ArrayList<String> {}
class MyException extends Exception {}
abstract class ReportMe implements List {}
