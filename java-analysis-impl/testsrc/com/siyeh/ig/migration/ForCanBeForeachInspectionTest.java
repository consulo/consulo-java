package com.siyeh.ig.migration;

import com.siyeh.ig.IGInspectionTestCase;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;

public class ForCanBeForeachInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/migration/foreach",
           new LocalInspectionToolWrapper(new ForCanBeForeachInspection()), "java 1.5");
  }
}